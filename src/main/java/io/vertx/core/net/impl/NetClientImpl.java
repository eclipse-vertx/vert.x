/*
 * Copyright (c) 2011-2013 The original author or authors
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

package io.vertx.core.net.impl;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.vertx.core.AsyncResult;
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.TCPMetrics;

/**
 *
 * This class is thread-safe
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NetClientImpl implements NetClient, MetricsProvider {

  private static final Logger log = LoggerFactory.getLogger(NetClientImpl.class);

  private final VertxInternal vertx;
  private final NetClientOptions options;
  private final SSLHelper sslHelper;
  private final Map<Channel, NetSocketImpl> socketMap = new ConcurrentHashMap<>();
  private final Closeable closeHook;
  private final ContextImpl creatingContext;
  private final TCPMetrics metrics;
  private final boolean logEnabled;
  private volatile boolean closed;

  public NetClientImpl(VertxInternal vertx, NetClientOptions options) {
    this(vertx, options, true);
  }

  public NetClientImpl(VertxInternal vertx, NetClientOptions options, boolean useCreatingContext) {
    this.vertx = vertx;
    this.options = new NetClientOptions(options);
    this.sslHelper = new SSLHelper(options, options.getKeyCertOptions(), options.getTrustOptions());
    this.logEnabled = options.getLogActivity();
    this.closeHook = completionHandler -> {
      NetClientImpl.this.close();
      completionHandler.handle(Future.succeededFuture());
    };
    if (useCreatingContext) {
      creatingContext = vertx.getContext();
      if (creatingContext != null) {
        if (creatingContext.isMultiThreadedWorkerContext()) {
          throw new IllegalStateException("Cannot use NetClient in a multi-threaded worker verticle");
        }
        creatingContext.addCloseHook(closeHook);
      }
    } else {
      creatingContext = null;
    }
    this.metrics = vertx.metricsSPI().createMetrics(this, options);
  }

  public synchronized NetClient connect(int port, String host, Handler<AsyncResult<NetSocket>> connectHandler) {
    checkClosed();
    connect(port, host, null, connectHandler, options.getReconnectAttempts());
    return this;
  }

  @Override
  public NetClient connect(int port, String host, String serverName, Handler<AsyncResult<NetSocket>> connectHandler) {
    checkClosed();
    connect(port, host, serverName, connectHandler, options.getReconnectAttempts());
    return this;
  }

  @Override
  public void close() {
    if (!closed) {
      for (NetSocket sock : socketMap.values()) {
        sock.close();
      }
      if (creatingContext != null) {
        creatingContext.removeCloseHook(closeHook);
      }
      closed = true;
      metrics.close();
    }
  }

  @Override
  public boolean isMetricsEnabled() {
    return metrics != null && metrics.isEnabled();
  }

  @Override
  public Metrics getMetrics() {
    return metrics;
  }

  private void checkClosed() {
    if (closed) {
      throw new IllegalStateException("Client is closed");
    }
  }

  private void applyConnectionOptions(Bootstrap bootstrap) {
    if (options.getLocalAddress() != null) {
      bootstrap.localAddress(options.getLocalAddress(), 0);
    }
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
  }

  private void connect(int port, String host, String serverName, Handler<AsyncResult<NetSocket>> connectHandler,
      int remainingAttempts) {
    Objects.requireNonNull(host, "No null host accepted");
    Objects.requireNonNull(connectHandler, "No null connectHandler accepted");
    ContextImpl context = vertx.getOrCreateContext();
    sslHelper.validate(vertx);
    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(context.nettyEventLoop());
    bootstrap.channel(NioSocketChannel.class);

    applyConnectionOptions(bootstrap);

    ChannelProvider channelProvider;
    if (options.getProxyOptions() == null) {
      channelProvider = ChannelProvider.INSTANCE;
    } else {
      channelProvider = ProxyChannelProvider.INSTANCE;
    }

    Handler<Channel> channelInitializer = ch -> {

      if (sslHelper.isSSL()) {
        SslHandler sslHandler = new SslHandler(sslHelper.createEngine(vertx, host, port, serverName));
        ch.pipeline().addLast("ssl", sslHandler);
      }

      ChannelPipeline pipeline = ch.pipeline();
      if (logEnabled) {
        pipeline.addLast("logging", new LoggingHandler());
      }
      if (sslHelper.isSSL()) {
        // only add ChunkedWriteHandler when SSL is enabled otherwise it is not needed as FileRegion is used.
        pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());       // For large file / sendfile support
      }
      if (options.getIdleTimeout() > 0) {
        pipeline.addLast("idle", new IdleStateHandler(0, 0, options.getIdleTimeout()));
      }
      pipeline.addLast("handler", new VertxNetHandler<NetSocketImpl>(ch, socketMap) {
        @Override
        protected void handleMsgReceived(NetSocketImpl conn, Object msg) {
          ByteBuf buf = (ByteBuf) msg;
          conn.handleDataReceived(Buffer.buffer(buf));
        }
      });
    };

    Handler<AsyncResult<Channel>> channelHandler = res -> {
      if (res.succeeded()) {

        Channel ch = res.result();

        if (sslHelper.isSSL()) {
          // TCP connected, so now we must do the SSL handshake
          SslHandler sslHandler = (SslHandler) ch.pipeline().get("ssl");

          io.netty.util.concurrent.Future<Channel> fut = sslHandler.handshakeFuture();
          fut.addListener(future2 -> {
            if (future2.isSuccess()) {
              connected(context, ch, connectHandler, host, port);
            } else {
              failed(context, ch, future2.cause(), connectHandler);
            }
          });
        } else {
          connected(context, ch, connectHandler, host, port);
        }

      } else {
        if (remainingAttempts > 0 || remainingAttempts == -1) {
          context.executeFromIO(() -> {
            log.debug("Failed to create connection. Will retry in " + options.getReconnectInterval() + " milliseconds");
            //Set a timer to retry connection
            vertx.setTimer(options.getReconnectInterval(), tid ->
                connect(port, host, serverName, connectHandler, remainingAttempts == -1 ? remainingAttempts : remainingAttempts - 1)
            );
          });
        } else {
          failed(context, null, res.cause(), connectHandler);
        }
      }
    };

    channelProvider.connect(vertx, bootstrap, options.getProxyOptions(), host, port, channelInitializer, channelHandler);
  }

  private void connected(ContextImpl context, Channel ch, Handler<AsyncResult<NetSocket>> connectHandler, String host, int port) {
    // Need to set context before constructor is called as writehandler registration needs this
    ContextImpl.setContext(context);
    NetSocketImpl sock = new NetSocketImpl(vertx, ch, host, port, context, sslHelper, metrics);
    VertxNetHandler handler = ch.pipeline().get(VertxNetHandler.class);
    handler.conn = sock;
    socketMap.put(ch, sock);
    context.executeFromIO(() -> {
      sock.metric(metrics.connected(sock.remoteAddress(), sock.remoteName()));
      connectHandler.handle(Future.succeededFuture(sock));
    });
  }

  private void failed(ContextImpl context, Channel ch, Throwable th, Handler<AsyncResult<NetSocket>> connectHandler) {
    if (ch != null) {
      ch.close();
    }
    context.executeFromIO(() -> doFailed(connectHandler, th));
  }

  private static void doFailed(Handler<AsyncResult<NetSocket>> connectHandler, Throwable th) {
    connectHandler.handle(Future.failedFuture(th));
  }

  @Override
  protected void finalize() throws Throwable {
    // Make sure this gets cleaned up if there are no more references to it
    // so as not to leave connections and resources dangling until the system is shutdown
    // which could make the JVM run out of file handles.
    close();
    super.finalize();
  }
}

