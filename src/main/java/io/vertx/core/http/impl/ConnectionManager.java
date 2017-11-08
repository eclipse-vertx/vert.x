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
  private final SSLHelper sslHelper;
  private final HttpClientOptions options;
  private final HttpClientImpl client;
  private final boolean keepAlive;
  private final boolean pipelining;
  private final int maxWaitQueueSize;
  private final int http2MaxConcurrency;
  private final boolean logEnabled;
  private final ChannelConnector connector;
  private final HttpClientMetrics metrics;

  ConnectionManager(HttpClientImpl client, HttpClientMetrics metrics) {
    this.client = client;
    this.sslHelper = client.getSslHelper();
    this.options = client.getOptions();
    this.vertx = client.getVertx();
    this.keepAlive = client.getOptions().isKeepAlive();
    this.pipelining = client.getOptions().isPipelining();
    this.maxWaitQueueSize = client.getOptions().getMaxWaitQueueSize();
    this.http2MaxConcurrency = options.getHttp2MultiplexingLimit() < 1 ? Integer.MAX_VALUE : options.getHttp2MultiplexingLimit();
    this.logEnabled = client.getOptions().getLogActivity();
    this.connector = new ChannelConnector();
    this.metrics = metrics;
  }

  HttpClientMetrics metrics() {
    return metrics;
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
      return queueMap.computeIfAbsent(key, targetAddress -> new ConnQueue(version, this, peerHost, host, port, ssl, key));
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
  class ConnQueue {

    private final QueueManager mgr;
    private final String peerHost;
    private final boolean ssl;
    private final int port;
    private final String host;
    private final ConnectionKey key;
    private final Queue<Waiter> waiters = new ArrayDeque<>();
    private Pool<HttpClientConnection> pool;
    private int connCount;
    private final int maxSize;
    final Object metric;

    ConnQueue(HttpVersion version, QueueManager mgr, String peerHost, String host, int port, boolean ssl, ConnectionKey key) {
      this.key = key;
      this.host = host;
      this.port = port;
      this.ssl = ssl;
      this.peerHost = peerHost;
      this.mgr = mgr;
      if (version == HttpVersion.HTTP_2) {
        maxSize = options.getHttp2MaxPoolSize();
        pool =  (Pool)new Http2Pool(this, client, metrics, mgr.connectionMap, http2MaxConcurrency, logEnabled, options.getHttp2MaxPoolSize(), options.getHttp2ConnectionWindowSize(), options.isHttp2ClearTextUpgrade());
      } else {
        maxSize = options.getMaxPoolSize();
        pool = (Pool)new Http1xPool(client, metrics, options, this, mgr.connectionMap, version, options.getMaxPoolSize(), host, port);
      }
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
          HttpClientConnection conn = pool.pollConnection();
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
      sslHelper.validate(vertx);
      Bootstrap bootstrap = new Bootstrap();
      bootstrap.group(waiter.context.nettyEventLoop());
      bootstrap.channel(vertx.transport().channelType(false));
      connector.connect(this, bootstrap, waiter.context, peerHost, ssl, pool.version(), host, port, ar -> {
        if (ar.succeeded()) {
          initConnection(waiter, ar.result());
        } else {

          // If no specific exception handler is provided, fall back to the HttpClient's exception handler.
          // If that doesn't exist just log it
          // Handler<Throwable> exHandler =
          //  waiter == null ? log::error : waiter::handleFailure;

          waiter.context.executeFromIO(() -> {
            closeConnection();
            waiter.handleFailure(ar.cause());
          });
        }
      });
    }

    private synchronized void initConnection(Waiter waiter, HttpClientConnection conn) {
      conn.lifecycleHandler(reuse -> {
        if (reuse) {
          recycleConnection(conn);
        } else {
          evictConnection(conn);
        }
        checkPending();
      });
      conn.getContext().executeFromIO(() -> {
        waiter.handleConnection(conn);
      });
      if (waiter.isCancelled()) {
        recycleConnection(conn);
      } else {
        deliverInternal(conn, waiter);
      }
      checkPending();
    }

    private synchronized void evictConnection(HttpClientConnection conn) {
      pool.evictConnection(conn);
    }

    private synchronized void recycleConnection(HttpClientConnection conn) {
      pool.recycleConnection(conn);
    }

    private void deliverInternal(HttpClientConnection conn, Waiter waiter) {
      if (conn.getContext().nettyEventLoop().inEventLoop()) {
        conn.getContext().executeFromIO(() -> {
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
        conn.getContext().runOnContext(v -> {
          if (conn.isValid()) {
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

    private void handshakeFailure(Channel ch, Throwable cause, Handler<AsyncResult<HttpClientConnection>> handler) {
      SSLHandshakeException sslException = new SSLHandshakeException("Failed to create SSL connection");
      if (cause != null) {
        sslException.initCause(cause);
      }
      connectFailed(ch, handler, sslException);
    }

    private void fallbackToHttp1x(Channel ch, ContextImpl context, HttpVersion fallbackVersion, int port, String host, Handler<AsyncResult<HttpClientConnection>> handler) {
      // change the pool to Http1xPool
      synchronized (this) {
        pool = (Pool)new Http1xPool(client, ConnectionManager.this.metrics, options, this, mgr.connectionMap, fallbackVersion, options.getMaxPoolSize(), host, port);
      }
      http1xConnected(context, ch, handler);
    }

    private void http1xConnected(ContextImpl context, Channel ch, Handler<AsyncResult<HttpClientConnection>> handler) {
      try {
        pool.createConnection(context, ch, handler);
      } catch (Exception e) {
        handler.handle(Future.failedFuture(e));
      }
    }

    private void http2Connected(ContextImpl context, Channel ch, Handler<AsyncResult<HttpClientConnection>> handler) {
      try {
        pool.createConnection(context, ch, handler);
      } catch (Exception e) {
        connectFailed(ch, handler, e);
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

  /**
   * The logic for the connection pool because HTTP/1 and HTTP/2 have different pooling logics.
   */
  interface Pool<C extends HttpClientConnection> {

    HttpVersion version();

    boolean canCreateStream(int connCount);

    void createConnection(ContextImpl context, Channel ch, Handler<AsyncResult<HttpClientConnection>> handler) throws Exception;

    C pollConnection();

    /**
     * Determine when a new connection should be created
     *
     * @param connCount the actual connection count including the one being created
     * @return true whether or not a new connection can be created
     */
    boolean canCreateConnection(int connCount);

    void closeAllConnections();

    void recycleConnection(C conn);

    HttpClientStream createStream(C conn) throws Exception;

    void evictConnection(C conn);

  }

  /**
   * The ChannelConnector performs the channel configuration and connection according to the
   * client options and the protocol version.
   * When the channel connects or fails to connect, it calls back the ConnQueue that initiated the
   * connection.
   */
  private class ChannelConnector {

    protected void connect(
        ConnQueue queue,
        Bootstrap bootstrap,
        ContextImpl context,
        String peerHost,
        boolean ssl,
        HttpVersion version,
        String host,
        int port,
        Handler<AsyncResult<HttpClientConnection>> handler) {

      applyConnectionOptions(options, bootstrap);

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
                  queue.http2Connected(context, ch, handler);
                } else {
                  applyHttp1xConnectionOptions(ch.pipeline(), context);
                  HttpVersion fallbackProtocol = "http/1.0".equals(protocol) ?
                    HttpVersion.HTTP_1_0 : HttpVersion.HTTP_1_1;
                  queue.fallbackToHttp1x(ch, context, fallbackProtocol, port, host, handler);
                }
              } else {
                applyHttp1xConnectionOptions(ch.pipeline(), context);
                queue.http1xConnected(context, ch, handler);
              }
            } else {
              queue.handshakeFailure(ch, fut.cause(), handler);
            }
          });
        } else {
          if (version == HttpVersion.HTTP_2) {
            if (options.isHttp2ClearTextUpgrade()) {
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
                    applyHttp1xConnectionOptions(ch.pipeline(), context);
                    queue.fallbackToHttp1x(ch, context, HttpVersion.HTTP_1_1, port, host, handler);
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
                  queue.http2Connected(context, ch, handler);
                }
              };
              HttpClientUpgradeHandler upgradeHandler = new HttpClientUpgradeHandler(httpCodec, upgradeCodec, 65536);
              ch.pipeline().addLast(httpCodec, upgradeHandler, new UpgradeRequestHandler());
            } else {
              applyHttp2ConnectionOptions(pipeline);
            }
          } else {
            applyHttp1xConnectionOptions(pipeline, context);
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
              if (version == HttpVersion.HTTP_2 && !options.isHttp2ClearTextUpgrade()) {
                queue.http2Connected(context, ch, handler);
              } else {
                queue.http1xConnected(context, ch, handler);
              }
            }
          }
        } else {
          queue.connectFailed(null, handler, res.cause());
        }
      };

      channelProvider.connect(vertx, bootstrap, options.getProxyOptions(), SocketAddress.inetSocketAddress(port, host), channelInitializer, channelHandler);
    }

    void applyConnectionOptions(HttpClientOptions options, Bootstrap bootstrap) {
      vertx.transport().configure(options, bootstrap);
    }

    void applyHttp2ConnectionOptions(ChannelPipeline pipeline) {
      if (options.getIdleTimeout() > 0) {
        pipeline.addLast("idle", new IdleStateHandler(0, 0, options.getIdleTimeout()));
      }
    }

    void applyHttp1xConnectionOptions(ChannelPipeline pipeline, ContextImpl context) {
      if (logEnabled) {
        pipeline.addLast("logging", new LoggingHandler());
      }
      pipeline.addLast("codec", new HttpClientCodec(options.getMaxInitialLineLength(), options.getMaxHeaderSize(),
              options.getMaxChunkSize(), false, false, options.getDecoderInitialBufferSize()));
      if (options.isTryUseCompression()) {
        pipeline.addLast("inflater", new HttpContentDecompressor(true));
      }
      if (options.getIdleTimeout() > 0) {
        pipeline.addLast("idle", new IdleStateHandler(0, 0, options.getIdleTimeout()));
      }
    }
  }
}
