/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http.impl;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.vertx.core.*;
import io.vertx.core.http.ConnectionPoolTooBusyException;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.ProxyType;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.ChannelProvider;
import io.vertx.core.net.impl.ProxyChannelProvider;
import io.vertx.core.net.impl.SSLHelper;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import javax.net.ssl.SSLHandshakeException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ConnectionManager {

  static final Logger log = LoggerFactory.getLogger(ConnectionManager.class);

  private final QueueManager wsQM = new QueueManager(); // The queue manager for websockets
  private final QueueManager requestQM = new QueueManager(); // The queue manager for requests
  private final VertxInternal vertx;
  private final HttpClientOptions options;
  private final HttpClientMetrics metrics;
  private final boolean keepAlive;
  private final boolean pipelining;
  private final int maxWaitQueueSize;
  private final ChannelConnector<HttpClientConnection> connector;

  ConnectionManager(HttpClientImpl client) {
    this.options = client.getOptions();
    this.vertx = client.getVertx();
    this.keepAlive = client.getOptions().isKeepAlive();
    this.pipelining = client.getOptions().isPipelining();
    this.maxWaitQueueSize = client.getOptions().getMaxWaitQueueSize();
    this.metrics = client.metrics();
    this.connector = new HttpChannelConnector(client);
  }

  static final class ConnectionKey {

    private final boolean ssl;
    private final int port;
    private final String host;

    public ConnectionKey(boolean ssl, int port, String host) {
      this.ssl = ssl;
      this.host = host;
      this.port = port;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ConnectionKey that = (ConnectionKey) o;

      if (ssl != that.ssl) return false;
      if (port != that.port) return false;
      if (!Objects.equals(host, that.host)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = ssl ? 1 : 0;
      result = 31 * result + (host != null ? host.hashCode() : 0);
      result = 31 * result + port;
      return result;
    }
  }

  /**
   * The queue manager manages the connection queues for a given usage, the idea is to split
   * queues for HTTP requests and websockets. A websocket uses a pool of connections
   * usually ugpraded from HTTP/1.1, HTTP requests may ask for HTTP/2 connections but obtain
   * only HTTP/1.1 connections.
   */
  private class QueueManager {

    private final Map<Channel, HttpClientConnection> connectionMap = new ConcurrentHashMap<>();
    private final Map<ConnectionKey, ConnQueue> queueMap = new ConcurrentHashMap<>();

    ConnQueue getConnQueue(String peerHost, boolean ssl, int port, String host, HttpVersion version) {
      ConnectionKey key = new ConnectionKey(ssl, port, peerHost);
      return queueMap.computeIfAbsent(key, targetAddress -> new ConnQueue(version, this, connector, peerHost, host, port, ssl, key));
    }

    public void close() {
      for (ConnQueue queue: queueMap.values()) {
        queue.closeAllConnections();
      }
      queueMap.clear();
      for (HttpClientConnection conn : connectionMap.values()) {
        conn.close();
      }
    }
  }

  public void getConnectionForWebsocket(boolean ssl, int port, String host, Waiter waiter) {
    ConnQueue connQueue = wsQM.getConnQueue(host, ssl, port, host, HttpVersion.HTTP_1_1);
    connQueue.getConnection(waiter);
  }

  public void getConnectionForRequest(HttpVersion version, String peerHost, boolean ssl, int port, String host, Waiter waiter) {
    if (!keepAlive && pipelining) {
      waiter.handleFailure(new IllegalStateException("Cannot have pipelining with no keep alive"));
    } else {
      ConnQueue connQueue = requestQM.getConnQueue(peerHost, ssl, port, host, version);
      connQueue.getConnection(waiter);
    }
  }

  public void close() {
    wsQM.close();
    requestQM.close();
    if (metrics != null) {
      metrics.close();
    }
  }

  /**
   * The connection queue delegates to the connection pool, the pooling strategy.
   *
   * - HTTP/1.x pools several connections
   * - HTTP/2 uses a single connection
   *
   * After a queue is initialized with an HTTP/2 pool, this pool changed to an HTTP/1/1
   * pool if the server does not support HTTP/2 or after negotiation. In this situation
   * all waiters on this queue will use HTTP/1.1 connections.
   */
  class ConnQueue<C> implements ClientConnectionListener<C> {

    private final QueueManager mgr;
    private final String peerHost;
    private final boolean ssl;
    private final int port;
    private final String host;
    private final ConnectionKey key;
    private final Queue<Waiter> waiters = new ArrayDeque<>();
    private final Pool<C> pool;
    private int connCount;
    private final int maxSize;
    private final ChannelConnector<C> connector;
    final Object metric;

    ConnQueue(HttpVersion version,
              QueueManager mgr,
              ChannelConnector<C> connector,
              String peerHost, String host, int port, boolean ssl, ConnectionKey key) {
      this.key = key;
      this.host = host;
      this.port = port;
      this.ssl = ssl;
      this.peerHost = peerHost;
      this.connector = connector;
      this.mgr = mgr;
      if (version == HttpVersion.HTTP_2) {
        maxSize = options.getHttp2MaxPoolSize();
      } else {
        maxSize = options.getMaxPoolSize();
      }
      pool =  (Pool)new HttpClientPool(version, options,  this, host, port);
      this.metric = metrics != null ? metrics.createEndpoint(host, port, maxSize) : null;
    }

    private void closeAllConnections() {
      pool.closeAllConnections();
    }

    private synchronized void getConnection(Waiter waiter) {
      // Enqueue
      if (maxWaitQueueSize < 0 || waiters.size() < maxWaitQueueSize || pool.canCreateStream(connCount)) {
        if (metrics != null) {
          waiter.metric = metrics.enqueueRequest(metric);
        }
        waiters.add(waiter);
        checkPending();
      } else {
        waiter.handleFailure(new ConnectionPoolTooBusyException("Connection pool reached max wait queue size of " + maxWaitQueueSize));
      }
    }

    private synchronized void checkPending() {
      while (true) {
        Waiter waiter = waiters.peek();
        if (waiter == null) {
          break;
        }
        if (metric != null) {
          metrics.dequeueRequest(metric, waiter.metric);
        }
        if (waiter.isCancelled()) {
          waiters.poll();
        } else {
          C conn = pool.pollConnection();
          if (conn != null) {
            waiters.poll();
            deliverInternal(conn, waiter);
          } else if (pool.canCreateConnection(connCount)) {
            waiters.poll();
            createConnection(waiter);
          } else {
            break;
          }
        }
      }
    }

    private void createConnection(Waiter waiter) {
      connCount++;
      Bootstrap bootstrap = new Bootstrap();
      bootstrap.group(waiter.context.nettyEventLoop());
      bootstrap.channel(vertx.transport().channelType(false));
      connector.connect(this, metric, bootstrap, waiter.context, peerHost, ssl, host, port, ar -> {
        if (ar.succeeded()) {
          initConnection(waiter, ar.result());
        } else {

          // If no specific exception handler is provided, fall back to the HttpClient's exception handler.
          // If that doesn't exist just log it
          // Handler<Throwable> exHandler =
          //  waiter == null ? log::error : waiter::handleFailure;

          closeConnection();

          waiter.context.executeFromIO(() -> {
            waiter.handleFailure(ar.cause());
          });
        }
      });
    }

    @Override
    public synchronized void onRecycle(C conn) {
      pool.recycleConnection(conn);
      checkPending();
    }

    @Override
    public synchronized void onClose(C conn, Channel channel) {
      mgr.connectionMap.remove(channel);
      pool.evictConnection(conn);
      closeConnection();
    }

    private synchronized void initConnection(Waiter waiter, C conn) {
      mgr.connectionMap.put(connector.channel(conn), (HttpClientConnection) conn);
      pool.initConnection(conn);
      pool.getContext(conn).executeFromIO(() -> {
        waiter.handleConnection((HttpClientConnection) conn);
      });
      if (waiter.isCancelled()) {
        pool.recycleConnection(conn);
      } else {
        deliverInternal(conn, waiter);
      }
      checkPending();
    }

    private void deliverInternal(C conn, Waiter waiter) {
      ContextImpl ctx = pool.getContext(conn);
      if (ctx.nettyEventLoop().inEventLoop()) {
        ctx.executeFromIO(() -> {
          HttpClientStream stream;
          try {
            stream = pool.createStream(conn);
          } catch (Exception e) {
            getConnection(waiter);
            return;
          }
          waiter.handleStream(stream);
        });
      } else {
        ctx.runOnContext(v -> {
          if (pool.isValid(conn)) {
            deliverInternal(conn, waiter);
          } else {
            getConnection(waiter);
          }
        });
      }
    }

    // Called if the connection is actually closed OR the connection attempt failed
    synchronized void closeConnection() {
      connCount--;
      checkPending();
      if (connCount == 0) {
        // No waiters and no connections - remove the ConnQueue
        mgr.queueMap.remove(key);
        if (metrics != null) {
          metrics.closeEndpoint(host, port, metric);
        }
      }
    }
  }

  /**
   * The logic for the connection pool because HTTP/1 and HTTP/2 have different pooling logics.
   */
  interface Pool<C> {

    boolean canCreateStream(int connCount);

    C pollConnection();

    /**
     * Determine when a new connection should be created
     *
     * @param connCount the actual connection count including the one being created
     * @return true whether or not a new connection can be created
     */
    boolean canCreateConnection(int connCount);

    void closeAllConnections();

    void initConnection(C conn);

    void recycleConnection(C conn);

    HttpClientStream createStream(C conn) throws Exception;

    void evictConnection(C conn);

    boolean isValid(C conn);

    ContextImpl getContext(C conn);

  }

  interface ChannelConnector<C> {

    void connect(
      ClientConnectionListener<C> queue,
      Object endpointMetric,
      Bootstrap bootstrap,
      ContextImpl context,
      String peerHost,
      boolean ssl,
      String host,
      int port,
      Handler<AsyncResult<C>> handler);

    Channel channel(C conn);

  }

  /**
   * The ChannelConnector performs the channel configuration and connection according to the
   * client options and the protocol version.
   * When the channel connects or fails to connect, it calls back the ConnQueue that initiated the
   * connection.
   */
  private static class HttpChannelConnector implements ChannelConnector<HttpClientConnection> {

    private final HttpClientImpl client;
    private final HttpClientOptions options;
    private final HttpClientMetrics metrics;
    private final SSLHelper sslHelper;
    private final HttpVersion version;

    HttpChannelConnector(HttpClientImpl client) {
      this.client = client;
      this.options = client.getOptions();
      this.metrics = client.metrics();
      this.sslHelper = client.getSslHelper();
      this.version = options.getProtocolVersion();
    }

    @Override
    public Channel channel(HttpClientConnection conn) {
      return conn.channel();
    }

    public void connect(
      ClientConnectionListener<HttpClientConnection> listener,
      Object endpointMetric,
      Bootstrap bootstrap,
      ContextImpl context,
      String peerHost,
      boolean ssl,
      String host,
      int port,
      Handler<AsyncResult<HttpClientConnection>> handler) {

      applyConnectionOptions(bootstrap);

      ChannelProvider channelProvider;
      // http proxy requests are handled in HttpClientImpl, everything else can use netty proxy handler
      if (options.getProxyOptions() == null || !ssl && options.getProxyOptions().getType()==ProxyType.HTTP ) {
        channelProvider = ChannelProvider.INSTANCE;
      } else {
        channelProvider = ProxyChannelProvider.INSTANCE;
      }

      boolean useAlpn = options.isUseAlpn();
      Handler<Channel> channelInitializer = ch -> {

        // Configure pipeline
        ChannelPipeline pipeline = ch.pipeline();
        if (ssl) {
          SslHandler sslHandler = new SslHandler(sslHelper.createEngine(client.getVertx(), peerHost, port, options.isForceSni() ? peerHost : null));
          ch.pipeline().addLast("ssl", sslHandler);
          // TCP connected, so now we must do the SSL handshake
          sslHandler.handshakeFuture().addListener(fut -> {
            if (fut.isSuccess()) {
              String protocol = sslHandler.applicationProtocol();
              if (useAlpn) {
                if ("h2".equals(protocol)) {
                  applyHttp2ConnectionOptions(ch.pipeline());
                  http2Connected(listener, endpointMetric, context, ch, handler);
                } else {
                  applyHttp1xConnectionOptions(ch.pipeline());
                  HttpVersion fallbackProtocol = "http/1.0".equals(protocol) ?
                    HttpVersion.HTTP_1_0 : HttpVersion.HTTP_1_1;
                  http1xConnected(listener, fallbackProtocol, host, port, true, endpointMetric, context, ch, handler);
                }
              } else {
                applyHttp1xConnectionOptions(ch.pipeline());
                http1xConnected(listener, version, host, port, true, endpointMetric, context, ch, handler);
              }
            } else {
              handshakeFailure(ch, fut.cause(), handler);
            }
          });
        } else {
          if (version == HttpVersion.HTTP_2) {
            if (client.getOptions().isHttp2ClearTextUpgrade()) {
              HttpClientCodec httpCodec = new HttpClientCodec();
              class UpgradeRequestHandler extends ChannelInboundHandlerAdapter {
                @Override
                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                  DefaultFullHttpRequest upgradeRequest =
                      new DefaultFullHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
                  String hostHeader = peerHost;
                  if (port != 80) {
                    hostHeader += ":" + port;
                  }
                  upgradeRequest.headers().set(HttpHeaderNames.HOST, hostHeader);
                  ctx.writeAndFlush(upgradeRequest);
                  ctx.fireChannelActive();
                }
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                  if (msg instanceof LastHttpContent) {
                    ChannelPipeline p = ctx.pipeline();
                    p.remove(httpCodec);
                    p.remove(this);
                    // Upgrade handler will remove itself
                    applyHttp1xConnectionOptions(ch.pipeline());
                    http1xConnected(listener, HttpVersion.HTTP_1_1, host, port, false, endpointMetric, context, ch, handler);
                  }
                }
                @Override
                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                  super.userEventTriggered(ctx, evt);
                  if (evt == HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_SUCCESSFUL) {
                    ctx.pipeline().remove(this);
                    // Upgrade handler will remove itself
                  }
                }
              }
              VertxHttp2ClientUpgradeCodec upgradeCodec = new VertxHttp2ClientUpgradeCodec(client.getOptions().getInitialSettings()) {
                @Override
                public void upgradeTo(ChannelHandlerContext ctx, FullHttpResponse upgradeResponse) throws Exception {
                  applyHttp2ConnectionOptions(pipeline);
                  http2Connected(listener, endpointMetric, context, ch, handler);
                }
              };
              HttpClientUpgradeHandler upgradeHandler = new HttpClientUpgradeHandler(httpCodec, upgradeCodec, 65536);
              ch.pipeline().addLast(httpCodec, upgradeHandler, new UpgradeRequestHandler());
            } else {
              applyHttp2ConnectionOptions(pipeline);
            }
          } else {
            applyHttp1xConnectionOptions(pipeline);
          }
        }
      };

      Handler<AsyncResult<Channel>> channelHandler = res -> {

        if (res.succeeded()) {
          Channel ch = res.result();
          if (!ssl) {
            if (ch.pipeline().get(HttpClientUpgradeHandler.class) != null) {
              // Upgrade handler do nothing
            } else {
              if (version == HttpVersion.HTTP_2 && !client.getOptions().isHttp2ClearTextUpgrade()) {
                http2Connected(listener, endpointMetric, context, ch, handler);
              } else {
                http1xConnected(listener, version, host, port, false, endpointMetric, context, ch, handler);
              }
            }
          }
        } else {
          connectFailed(null, handler, res.cause());
        }
      };

      channelProvider.connect(client.getVertx(), bootstrap, client.getOptions().getProxyOptions(), SocketAddress.inetSocketAddress(port, host), channelInitializer, channelHandler);
    }

    private void applyConnectionOptions(Bootstrap bootstrap) {
      client.getVertx().transport().configure(options, bootstrap);
    }

    private void applyHttp2ConnectionOptions(ChannelPipeline pipeline) {
      if (client.getOptions().getIdleTimeout() > 0) {
        pipeline.addLast("idle", new IdleStateHandler(0, 0, options.getIdleTimeout()));
      }
    }

    private void applyHttp1xConnectionOptions(ChannelPipeline pipeline) {
      if (client.getOptions().getLogActivity()) {
        pipeline.addLast("logging", new LoggingHandler());
      }
      pipeline.addLast("codec", new HttpClientCodec(
        client.getOptions().getMaxInitialLineLength(),
        client.getOptions().getMaxHeaderSize(),
        client.getOptions().getMaxChunkSize(),
        false,
        false,
        client.getOptions().getDecoderInitialBufferSize()));
      if (client.getOptions().isTryUseCompression()) {
        pipeline.addLast("inflater", new HttpContentDecompressor(true));
      }
      if (client.getOptions().getIdleTimeout() > 0) {
        pipeline.addLast("idle", new IdleStateHandler(0, 0, client.getOptions().getIdleTimeout()));
      }
    }

    private void handshakeFailure(Channel ch, Throwable cause, Handler<AsyncResult<HttpClientConnection>> handler) {
      SSLHandshakeException sslException = new SSLHandshakeException("Failed to create SSL connection");
      if (cause != null) {
        sslException.initCause(cause);
      }
      connectFailed(ch, handler, sslException);
    }

    private void http1xConnected(ClientConnectionListener<HttpClientConnection> queue,
                                 HttpVersion version,
                                 String host,
                                 int port,
                                 boolean ssl,
                                 Object endpointMetric,
                                 ContextImpl context,
                                 Channel ch,
                                 Handler<AsyncResult<HttpClientConnection>> handler) {
      synchronized (this) {
        ClientHandler clientHandler = new ClientHandler(
          queue,
          context,
          version,
          host,
          port,
          ssl,
          client,
          endpointMetric,
          client.metrics());
        clientHandler.addHandler(conn -> {
          handler.handle(Future.succeededFuture(conn));
        });
        clientHandler.removeHandler(conn -> {
          queue.onClose(conn, ch);
        });
        ch.pipeline().addLast("handler", clientHandler);
      }
    }

    private void http2Connected(ClientConnectionListener<HttpClientConnection> queue, Object endpointMetric, ContextImpl context, Channel ch, Handler<AsyncResult<HttpClientConnection>> resultHandler) {
      try {
        synchronized (this) {
          boolean upgrade;
          upgrade = ch.pipeline().get(SslHandler.class) == null && options.isHttp2ClearTextUpgrade();
          VertxHttp2ConnectionHandler<Http2ClientConnection> handler = new VertxHttp2ConnectionHandlerBuilder<Http2ClientConnection>(ch)
            .server(false)
            .clientUpgrade(upgrade)
            .useCompression(client.getOptions().isTryUseCompression())
            .initialSettings(client.getOptions().getInitialSettings())
            .connectionFactory(connHandler -> {
              Http2ClientConnection conn = new Http2ClientConnection(queue, endpointMetric, client, context, connHandler, metrics, resultHandler);
              return conn;
            })
            .logEnabled(options.getLogActivity())
            .build();
          handler.addHandler(conn -> {
            if (options.getHttp2ConnectionWindowSize() > 0) {
              conn.setWindowSize(options.getHttp2ConnectionWindowSize());
            }
            if (metrics != null) {
              Object metric = metrics.connected(conn.remoteAddress(), conn.remoteName());
              conn.metric(metric);
            }
            resultHandler.handle(Future.succeededFuture(conn));
          });
          handler.removeHandler(conn -> {
            if (metrics != null) {
              metrics.endpointDisconnected(endpointMetric, conn.metric());
            }
            queue.onClose(conn, ch);
          });
        }
      } catch (Exception e) {
        connectFailed(ch, resultHandler, e);
      }
    }

    private void connectFailed(Channel ch, Handler<AsyncResult<HttpClientConnection>> connectionExceptionHandler, Throwable t) {
      if (ch != null) {
        try {
          ch.close();
        } catch (Exception ignore) {
        }
      }
      connectionExceptionHandler.handle(Future.failedFuture(t));
    }
  }
}
