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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.ConnectionPoolTooBusyException;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.proxy.ProxyChannelProvider;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.impl.AsyncResolveBindConnectHelper;
import io.vertx.core.net.impl.PartialPooledByteBufAllocator;
import io.vertx.core.net.impl.SSLHelper;

import javax.net.ssl.SSLHandshakeException;
import java.util.ArrayDeque;
import java.util.Map;
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

  ConnectionManager(HttpClientImpl client) {
    this.client = client;
    this.sslHelper = client.getSslHelper();
    this.options = client.getOptions();
    this.vertx = client.getVertx();
    this.keepAlive = client.getOptions().isKeepAlive();
    this.pipelining = client.getOptions().isPipelining();
    this.maxWaitQueueSize = client.getOptions().getMaxWaitQueueSize();
  }

  /**
   * The queue manager manages the connection queues for a given usage, the idea is to split
   * queues for HTTP requests and websockets. A websocket uses a pool of connections
   * usually ugpraded from HTTP/1.1, HTTP requests may ask for HTTP/2 connections but obtain
   * only HTTP/1.1 connections.
   */
  private class QueueManager {

    private final Map<Channel, HttpClientConnection> connectionMap = new ConcurrentHashMap<>();
    private final Map<TargetAddress, ConnQueue> queueMap = new ConcurrentHashMap<>();

    ConnQueue getConnQueue(TargetAddress address, HttpVersion version) {
      ConnQueue connQueue = queueMap.get(address);
      if (connQueue == null) {
        connQueue = new ConnQueue(version, this, address);
        ConnQueue prev = queueMap.putIfAbsent(address, connQueue);
        if (prev != null) {
          connQueue = prev;
        }
      }
      return connQueue;
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

  public void getConnectionForWebsocket(int port, String host, Waiter waiter) {
//    if (!keepAlive && pipelining) {
//      waiter.handleFailure(new IllegalStateException("Cannot have pipelining with no keep alive"));
//    } else {
//    }
    TargetAddress address = new TargetAddress(host, port);
    ConnQueue connQueue = wsQM.getConnQueue(address, HttpVersion.HTTP_1_1);
    connQueue.getConnection(waiter);
  }

  public void getConnectionForRequest(HttpVersion version, int port, String host, Waiter waiter) {
    if (!keepAlive && pipelining) {
      waiter.handleFailure(new IllegalStateException("Cannot have pipelining with no keep alive"));
    } else {
      TargetAddress address = new TargetAddress(host, port);
      ConnQueue connQueue = requestQM.getConnQueue(address, version);
      connQueue.getConnection(waiter);
    }
  }

  public void close() {
    wsQM.close();
    requestQM.close();
  }

  static class TargetAddress {
    final String host;
    final int port;

    TargetAddress(String host, int port) {
      this.host = host;
      this.port = port;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      TargetAddress that = (TargetAddress) o;
      if (port != that.port) return false;
      if (host != null ? !host.equals(that.host) : that.host != null) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = host != null ? host.hashCode() : 0;
      result = 31 * result + port;
      return result;
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
  public class ConnQueue {

    private final QueueManager mgr;
    private final TargetAddress address;
    private final Queue<Waiter> waiters = new ArrayDeque<>();
    private Pool<? extends HttpClientConnection> pool;
    private int connCount;

    ConnQueue(HttpVersion version, QueueManager mgr, TargetAddress address) {
      this.address = address;
      this.mgr = mgr;
      if (version == HttpVersion.HTTP_2) {
        pool =  new Http2Pool(this, client, mgr.connectionMap);
      } else {
        pool = new Http1xPool(client, options, this, mgr.connectionMap, version);
      }
    }

    public synchronized void getConnection(Waiter waiter) {
      boolean served = pool.getConnection(waiter);
      if (!served) {
        if (connCount == pool.maxSockets) {
          // Wait in queue
          if (maxWaitQueueSize < 0 || waiters.size() < maxWaitQueueSize) {
            waiters.add(waiter);
          } else {
            waiter.handleFailure(new ConnectionPoolTooBusyException("Connection pool reached max wait queue size of " + maxWaitQueueSize));
          }
        } else {
          // Create a new connection
          createNewConnection(waiter);
        }
      }
    }

    void closeAllConnections() {
      pool.closeAllConnections();
    }

    private void createNewConnection(Waiter waiter) {
      connCount++;
      internalConnect(pool.version(), address.host, address.port, waiter);
    }

    /**
     * @return the next non-canceled waiters in the queue
     */
    Waiter getNextWaiter() {
      Waiter waiter = waiters.poll();
      while (waiter != null && waiter.isCancelled()) {
        waiter = waiters.poll();
      }
      return waiter;
    }

    // Called if the connection is actually closed OR the connection attempt failed
    public synchronized void connectionClosed() {
      connCount--;
      Waiter waiter = getNextWaiter();
      if (waiter != null) {
        // There's a waiter - so it can have a new connection
        createNewConnection(waiter);
      } else if (connCount == 0) {
        // No waiters and no connections - remove the ConnQueue
        mgr.queueMap.remove(address);
      }
    }

    protected void internalConnect(HttpVersion version, String host, int port, Waiter waiter) {
      ContextImpl context;
      if (waiter.context == null) {
        // Embedded
        context = vertx.getOrCreateContext();
      } else {
        context = waiter.context;
      }
      sslHelper.validate(vertx);

      Bootstrap bootstrap = new Bootstrap();
      bootstrap.group(context.nettyEventLoop());
      bootstrap.channel(NioSocketChannel.class);
      applyConnectionOptions(options, bootstrap);

      //
      ChannelProvider channelProvider;
      if (options.getProxyHost() == null) {
        channelProvider = new ChannelProvider() {
          @Override
          public void connect(VertxInternal vertx, Bootstrap bootstrap, HttpClientOptions options, String host, int port, Handler<AsyncResult<Channel>> channelHandler) {
            bootstrap.handler(new ChannelInitializer<Channel>() {
              @Override
              protected void initChannel(Channel ch) throws Exception {
              }
            });
            AsyncResolveBindConnectHelper<ChannelFuture> future = AsyncResolveBindConnectHelper.doConnect(vertx, port, host, bootstrap);
            future.addListener(res -> {
              if (res.succeeded()) {
                channelHandler.handle(Future.succeededFuture(res.result().channel()));
              } else {
                channelHandler.handle(Future.failedFuture(res.cause()));
              }
            });
          }
        };
      } else {
        channelProvider = new ProxyChannelProvider();
      }

      Handler<AsyncResult<Channel>> channelHandler = res -> {

        if (res.succeeded()) {
          Channel ch = res.result();

          // Configure pipeline
          ChannelPipeline pipeline = ch.pipeline();
          boolean useAlpn = options.isUseAlpn();
          if (useAlpn) {
            SslHandler sslHandler = sslHelper.createSslHandler(client.getVertx(), host, port);
            ch.pipeline().addLast(sslHandler);
            ch.pipeline().addLast(new ApplicationProtocolNegotiationHandler("http/1.1") {
              @Override
              protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                  http2Connected(context, ch, waiter, false);
                } else if (ApplicationProtocolNames.HTTP_1_1.equals(protocol)) {
                  fallbackToHttp1x(ch, context, HttpVersion.HTTP_1_1, port, host, waiter);
                } else {
                  fallbackToHttp1x(ch, context, HttpVersion.HTTP_1_0, port, host, waiter);
                }
              }
            });
          } else {
            if (options.isSsl()) {
              pipeline.addLast("ssl", sslHelper.createSslHandler(vertx, host, port));
            }
            if (version == HttpVersion.HTTP_2) {
              if (options.isH2cUpgrade()) {
                HttpClientCodec httpCodec = new HttpClientCodec();
                class UpgradeRequestHandler extends ChannelInboundHandlerAdapter {
                  @Override
                  public void channelActive(ChannelHandlerContext ctx) throws Exception {
                    DefaultFullHttpRequest upgradeRequest =
                        new DefaultFullHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
                    ctx.writeAndFlush(upgradeRequest);
                    ctx.fireChannelActive();
                  }
                  @Override
                  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                    super.userEventTriggered(ctx, evt);
                    ChannelPipeline p = ctx.pipeline();
                    if (evt == HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_SUCCESSFUL) {
                      p.remove(this);
                      // Upgrade handler will remove itself
                    } else if (evt == HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_REJECTED) {
                      p.remove(httpCodec);
                      p.remove(this);
                      // Upgrade handler will remove itself
                      fallbackToHttp1x(ch, context, HttpVersion.HTTP_1_1, port, host, waiter);
                    }
                  }
                }
                VertxHttp2ClientUpgradeCodec upgradeCodec = new VertxHttp2ClientUpgradeCodec(client.getOptions().getInitialSettings()) {
                  @Override
                  public void upgradeTo(ChannelHandlerContext ctx, FullHttpResponse upgradeResponse) throws Exception {
                    http2Connected(context, ch, waiter, true);
                  }
                };
                HttpClientUpgradeHandler upgradeHandler = new HttpClientUpgradeHandler(httpCodec, upgradeCodec, 65536);
                ch.pipeline().addLast(httpCodec, upgradeHandler, new UpgradeRequestHandler());
              } else {
                applyH2ConnectionOptions(pipeline);
              }
            } else {
              applyHttp1xConnectionOptions(pipeline, context);
            }
          }

          //
          if (options.isSsl()) {
            // TCP connected, so now we must do the SSL handshake
            SslHandler sslHandler = ch.pipeline().get(SslHandler.class);
            io.netty.util.concurrent.Future<Channel> fut = sslHandler.handshakeFuture();
            fut.addListener(fut2 -> {
              if (fut2.isSuccess()) {
                if (!options.isUseAlpn()) {
                  http1xConnected(version, context, port, host, ch, waiter);
                }
              } else {
                handshakeFailure(context, ch, fut2.cause(), waiter);
              }
            });
          } else {
            if (!options.isUseAlpn()) {
              if (ch.pipeline().get(HttpClientUpgradeHandler.class) != null) {
                // Upgrade handler do nothing
              } else {
                if (version == HttpVersion.HTTP_2 && !options.isH2cUpgrade()) {
                  http2Connected(context, ch, waiter, false);
                } else {
                  http1xConnected(version, context, port, host, ch, waiter);
                }
              }
            }
          }
        } else {
          connectionFailed(context, null, waiter::handleFailure, res.cause());
        }
      };

      try {
        channelProvider.connect(vertx, bootstrap, options, host, port, channelHandler);
      } catch (NoClassDefFoundError e) {
        if (options.getProxyHost() != null && e.getMessage().contains("io/netty/handler/proxy")) {
          log.warn("Depedency io.netty:netty-handler-proxy missing - check your classpath");
          channelHandler.handle(Future.failedFuture(e));
        }
      }
    }

    private void handshakeFailure(ContextImpl context, Channel ch, Throwable cause, Waiter waiter) {
      SSLHandshakeException sslException = new SSLHandshakeException("Failed to create SSL connection");
      if (cause != null) {
        sslException.initCause(cause);
      }
      connectionFailed(context, ch, waiter::handleFailure, sslException);
    }

    private void fallbackToHttp1x(Channel ch, ContextImpl context, HttpVersion fallbackVersion, int port, String host, Waiter waiter) {
      // change the pool to Http1xPool
      synchronized (this) {
        pool = new Http1xPool(client, options, this, mgr.connectionMap, fallbackVersion);
      }
      applyHttp1xConnectionOptions(ch.pipeline(), context);
      http1xConnected(fallbackVersion, context, port, host, ch, waiter);
    }

    private void http1xConnected(HttpVersion version, ContextImpl context, int port, String host, Channel ch, Waiter waiter) {
      context.executeFromIO(() ->
          ((Http1xPool)pool).createConn(version, context, port, host, ch, waiter)
      );
    }

    private void http2Connected(ContextImpl context, Channel ch, Waiter waiter, boolean upgrade) {
      context.executeFromIO(() -> {
        try {
          ((Http2Pool)pool).createConn(context, ch, waiter, upgrade);
        } catch (Http2Exception e) {
          connectionFailed(context, ch, waiter::handleFailure, e);
        }
      });
    }

    private void connectionFailed(ContextImpl context, Channel ch, Handler<Throwable> connectionExceptionHandler,
                                  Throwable t) {
      // If no specific exception handler is provided, fall back to the HttpClient's exception handler.
      // If that doesn't exist just log it
      Handler<Throwable> exHandler =
          connectionExceptionHandler == null ? log::error : connectionExceptionHandler;

      context.executeFromIO(() -> {
        connectionClosed();
        try {
          ch.close();
        } catch (Exception ignore) {
        }
        exHandler.handle(t);
      });
    }

    void applyConnectionOptions(HttpClientOptions options, Bootstrap bootstrap) {
      bootstrap.option(ChannelOption.TCP_NODELAY, options.isTcpNoDelay());
      if (options.getSendBufferSize() != -1) {
        bootstrap.option(ChannelOption.SO_SNDBUF, options.getSendBufferSize());
      }
      if (options.getReceiveBufferSize() != -1) {
        bootstrap.option(ChannelOption.SO_RCVBUF, options.getReceiveBufferSize());
        bootstrap.option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(options.getReceiveBufferSize()));
      }
      if (options.getSoLinger() != -1) {
        bootstrap.option(ChannelOption.SO_LINGER, options.getSoLinger());
      }
      if (options.getTrafficClass() != -1) {
        bootstrap.option(ChannelOption.IP_TOS, options.getTrafficClass());
      }
      bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, options.getConnectTimeout());
      bootstrap.option(ChannelOption.ALLOCATOR, PartialPooledByteBufAllocator.INSTANCE);
      bootstrap.option(ChannelOption.SO_KEEPALIVE, options.isTcpKeepAlive());
      bootstrap.option(ChannelOption.SO_REUSEADDR, options.isReuseAddress());
    }

    void applyH2ConnectionOptions(ChannelPipeline pipeline) {
      if (options.getIdleTimeout() > 0) {
        pipeline.addLast("idle", new IdleStateHandler(0, 0, options.getIdleTimeout()));
      }
    }

    void applyHttp1xConnectionOptions(ChannelPipeline pipeline, ContextImpl context) {
      pipeline.addLast("codec", new HttpClientCodec(4096, 8192, options.getMaxChunkSize(), false, false));
      if (options.isTryUseCompression()) {
        pipeline.addLast("inflater", new HttpContentDecompressor(true));
      }
      if (options.getIdleTimeout() > 0) {
        pipeline.addLast("idle", new IdleStateHandler(0, 0, options.getIdleTimeout()));
      }
      pipeline.addLast("handler", new ClientHandler(pipeline.channel(), context, (Map)mgr.connectionMap));
    }
  }

  static abstract class Pool<C extends HttpClientConnection> {

    // Pools must locks on the queue object to keep a single lock
    final ConnQueue queue;
    final int maxSockets;

    Pool(ConnQueue queue, int maxSockets) {
      this.queue = queue;
      this.maxSockets = maxSockets;
    }

    abstract HttpVersion version();

    abstract boolean getConnection(Waiter waiter);

    abstract void closeAllConnections();

    abstract void recycle(C conn);

    abstract HttpClientStream createStream(C conn) throws Exception;

    /**
     * Handle the connection if the waiter is not cancelled, otherwise recycle the connection.
     *
     * @param conn the connection
     */
    void deliverStream(C conn, Waiter waiter) {
      if (!conn.isValid()) {
        // The connection has been closed - closed connections can be in the pool
        // Get another connection - Note that we DO NOT call connectionClosed() on the pool at this point
        // that is done asynchronously in the connection closeHandler()
        queue.getConnection(waiter);
      } else if (waiter.isCancelled()) {
        recycle(conn);
      } else {
        HttpClientStream stream;
        try {
          stream = createStream(conn);
        } catch (Exception e) {
          queue.getConnection(waiter);
          return;
        }
        waiter.handleStream(stream);
      }
    }
  }
}
