/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net.impl;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.vertx.core.AsyncResult;
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.TCPMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 *
 * This class is thread-safe
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class NetClientImpl implements MetricsProvider, NetClient {

  private static final Logger log = LoggerFactory.getLogger(NetClientImpl.class);
  protected final int idleTimeout;
  private final TimeUnit idleTimeoutUnit;
  protected final boolean logEnabled;

  private final VertxInternal vertx;
  private final NetClientOptions options;
  protected final SSLHelper sslHelper;
  private final Map<Channel, NetSocketImpl> socketMap = new ConcurrentHashMap<>();
  private final Closeable closeHook;
  private final ContextInternal creatingContext;
  private final TCPMetrics metrics;
  private volatile boolean closed;

  public NetClientImpl(VertxInternal vertx, NetClientOptions options) {
    this(vertx, options, true);
  }

  public NetClientImpl(VertxInternal vertx, NetClientOptions options, boolean useCreatingContext) {
    this.vertx = vertx;
    this.options = new NetClientOptions(options);
    this.sslHelper = new SSLHelper(options, options.getKeyCertOptions(), options.getTrustOptions());
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
    VertxMetrics metrics = vertx.metricsSPI();
    this.metrics = metrics != null ? metrics.createNetClientMetrics(options) : null;
    logEnabled = options.getLogActivity();
    idleTimeout = options.getIdleTimeout();
    idleTimeoutUnit = options.getIdleTimeoutUnit();
  }

  protected void initChannel(ChannelPipeline pipeline) {
    if (logEnabled) {
      pipeline.addLast("logging", new LoggingHandler());
    }
    if (sslHelper.isSSL()) {
      // only add ChunkedWriteHandler when SSL is enabled otherwise it is not needed as FileRegion is used.
      pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());       // For large file / sendfile support
    }
    if (idleTimeout > 0) {
      pipeline.addLast("idle", new IdleStateHandler(0, 0, idleTimeout, idleTimeoutUnit));
    }
  }

  public synchronized NetClient connect(int port, String host, Handler<AsyncResult<NetSocket>> connectHandler) {
    connect(port, host, null, connectHandler);
    return this;
  }

  @Override
  public NetClient connect(int port, String host, String serverName, Handler<AsyncResult<NetSocket>> connectHandler) {
    doConnect(SocketAddress.inetSocketAddress(port, host), serverName, connectHandler != null ? ar -> connectHandler.handle(ar.map(s -> (NetSocket) s)) : null);
    return this;
  }

  public void close() {
    if (!closed) {
      for (NetSocketImpl sock : socketMap.values()) {
        sock.close();
      }
      if (creatingContext != null) {
        creatingContext.removeCloseHook(closeHook);
      }
      closed = true;
      if (metrics != null) {
        metrics.close();
      }
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
    vertx.transport().configure(options, bootstrap);
  }

  @Override
  public NetClient connect(SocketAddress remoteAddress, String serverName, Handler<AsyncResult<NetSocket>> connectHandler) {
    doConnect(remoteAddress, serverName, connectHandler);
    return this;
  }

  @Override
  public NetClient connect(SocketAddress remoteAddress, Handler<AsyncResult<NetSocket>> connectHandler) {
    doConnect(remoteAddress, null, connectHandler);
    return this;
  }

  protected void doConnect(SocketAddress remoteAddress, String serverName, Handler<AsyncResult<NetSocket>> connectHandler) {
    doConnect(remoteAddress, serverName, connectHandler, options.getReconnectAttempts());
  }

  protected void doConnect(SocketAddress remoteAddress, String serverName, Handler<AsyncResult<NetSocket>> connectHandler,
                           int remainingAttempts) {
    checkClosed();
    Objects.requireNonNull(connectHandler, "No null connectHandler accepted");
    ContextInternal context = vertx.getOrCreateContext();
    sslHelper.validate(vertx);
    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(context.nettyEventLoop());
    bootstrap.channelFactory(vertx.transport().channelFactory(remoteAddress.path() != null));

    applyConnectionOptions(bootstrap);

    ChannelProvider channelProvider;
    if (options.getProxyOptions() == null) {
      channelProvider = ChannelProvider.INSTANCE;
    } else {
      channelProvider = ProxyChannelProvider.INSTANCE;
    }

    Handler<Channel> channelInitializer = ch -> {
      if (sslHelper.isSSL()) {
        SslHandler sslHandler = new SslHandler(sslHelper.createEngine(vertx, remoteAddress, serverName));
        ch.pipeline().addLast("ssl", sslHandler);
      }
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
              connected(context, ch, connectHandler, remoteAddress);
            } else {
              failed(context, ch, future2.cause(), connectHandler);
            }
          });
        } else {
          connected(context, ch, connectHandler, remoteAddress);
        }

      } else {
        if (remainingAttempts > 0 || remainingAttempts == -1) {
          context.executeFromIO(v -> {
            log.debug("Failed to create connection. Will retry in " + options.getReconnectInterval() + " milliseconds");
            //Set a timer to retry connection
            vertx.setTimer(options.getReconnectInterval(), tid ->
                doConnect(remoteAddress, serverName, connectHandler, remainingAttempts == -1 ? remainingAttempts : remainingAttempts - 1)
            );
          });
        } else {
          failed(context, null, res.cause(), connectHandler);
        }
      }
    };

    channelProvider.connect(vertx, bootstrap, options.getProxyOptions(), remoteAddress, channelInitializer, channelHandler);
  }

  private void connected(ContextInternal context, Channel ch, Handler<AsyncResult<NetSocket>> connectHandler, SocketAddress remoteAddress) {

    initChannel(ch.pipeline());

    VertxNetHandler handler = new VertxNetHandler(ctx -> new NetSocketImpl(vertx, ctx, remoteAddress, context, sslHelper, metrics));
    handler.addHandler(sock -> {
      socketMap.put(ch, sock);
      context.executeFromIO(v -> {
        if (metrics != null) {
          sock.metric(metrics.connected(sock.remoteAddress(), sock.remoteName()));
        }
        sock.registerEventBusHandler();
        connectHandler.handle(Future.succeededFuture(sock));
      });
    });
    handler.removeHandler(conn -> {
      socketMap.remove(ch);
    });
    ch.pipeline().addLast("handler", handler);
  }

  private void failed(ContextInternal context, Channel ch, Throwable th, Handler<AsyncResult<NetSocket>> connectHandler) {
    if (ch != null) {
      ch.close();
    }
    context.executeFromIO(v -> doFailed(connectHandler, th));
  }

  private void doFailed(Handler<AsyncResult<NetSocket>> connectHandler, Throwable th) {
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

