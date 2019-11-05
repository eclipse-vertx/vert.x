/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
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
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.AsyncResult;
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
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

import java.io.FileNotFoundException;
import java.net.ConnectException;
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

  @Override
  public Future<NetSocket> connect(int port, String host) {
    return connect(port, host, (String) null);
  }

  @Override
  public Future<NetSocket> connect(int port, String host, String serverName) {
    return connect(SocketAddress.inetSocketAddress(port, host), serverName);
  }

  @Override
  public Future<NetSocket> connect(SocketAddress remoteAddress) {
    return connect(remoteAddress, (String) null);
  }

  @Override
  public Future<NetSocket> connect(SocketAddress remoteAddress, String serverName) {
    ContextInternal ctx = vertx.getOrCreateContext();
    Promise<NetSocket> promise = ctx.promise();
    doConnect(remoteAddress, serverName, promise, ctx);
    return promise.future();
  }

  public NetClient connect(int port, String host, Handler<AsyncResult<NetSocket>> connectHandler) {
    return connect(port, host, null, connectHandler);
  }

  @Override
  public NetClient connect(int port, String host, String serverName, Handler<AsyncResult<NetSocket>> connectHandler) {
    return connect(SocketAddress.inetSocketAddress(port, host), serverName, connectHandler);
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
    return metrics != null;
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

  private void applyConnectionOptions(boolean domainSocket, Bootstrap bootstrap) {
    vertx.transport().configure(options, domainSocket, bootstrap);
  }

  @Override
  public NetClient connect(SocketAddress remoteAddress, String serverName, Handler<AsyncResult<NetSocket>> connectHandler) {
    Objects.requireNonNull(connectHandler, "No null connectHandler accepted");
    ContextInternal ctx = vertx.getOrCreateContext();
    Promise<NetSocket> promise = ctx.promise();
    promise.future().setHandler(connectHandler);
    doConnect(remoteAddress, serverName, promise, ctx);
    return this;
  }

  @Override
  public NetClient connect(SocketAddress remoteAddress, Handler<AsyncResult<NetSocket>> connectHandler) {
    return connect(remoteAddress, null, connectHandler);
  }

  private void doConnect(SocketAddress remoteAddress, String serverName, Promise<NetSocket> connectHandler, ContextInternal ctx) {
    doConnect(remoteAddress, serverName, connectHandler, ctx, options.getReconnectAttempts());
  }

  private void doConnect(SocketAddress remoteAddress, String serverName, Promise<NetSocket> connectHandler, ContextInternal context, int remainingAttempts) {
    checkClosed();
    Objects.requireNonNull(connectHandler, "No null connectHandler accepted");
    sslHelper.validate(vertx);
    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(context.nettyEventLoop());
    bootstrap.channelFactory(vertx.transport().channelFactory(remoteAddress.path() != null));

    applyConnectionOptions(remoteAddress.path() != null, bootstrap);

    ChannelProvider channelProvider = new ChannelProvider(bootstrap, sslHelper, context, options.getProxyOptions());

    SocketAddress peerAddress = remoteAddress;
    String peerHost = peerAddress.host();
    if (peerHost != null && peerHost.endsWith(".")) {
      peerAddress = SocketAddress.inetSocketAddress(peerAddress.port(), peerHost.substring(0, peerHost.length() - 1));
    }
    io.netty.util.concurrent.Future<Channel> fut = channelProvider.connect(remoteAddress, peerAddress, serverName, sslHelper.isSSL());
    fut.addListener((GenericFutureListener<io.netty.util.concurrent.Future<Channel>>) future -> {
      if (future.isSuccess()) {
        Channel ch = future.getNow();
        connected(context, ch, connectHandler, remoteAddress);
      } else {
        Throwable cause = future.cause();
        // FileNotFoundException for domain sockets
        boolean connectError = cause instanceof ConnectException || cause instanceof FileNotFoundException;
        if (connectError && (remainingAttempts > 0 || remainingAttempts == -1)) {
          context.emitFromIO(v -> {
            log.debug("Failed to create connection. Will retry in " + options.getReconnectInterval() + " milliseconds");
            //Set a timer to retry connection
            vertx.setTimer(options.getReconnectInterval(), tid ->
              doConnect(remoteAddress, serverName, connectHandler, context, remainingAttempts == -1 ? remainingAttempts : remainingAttempts - 1)
            );
          });
        } else {
          failed(context, null, cause, connectHandler);
        }
      }
    });
  }

  private void connected(ContextInternal context, Channel ch, Promise<NetSocket> connectHandler, SocketAddress remoteAddress) {

    initChannel(ch.pipeline());

    VertxHandler<NetSocketImpl> handler = VertxHandler.create(context, ctx -> new NetSocketImpl(vertx, ctx, remoteAddress, context, sslHelper, metrics));
    handler.addHandler(sock -> {
      socketMap.put(ch, sock);
      context.emitFromIO(v -> {
        if (metrics != null) {
          sock.metric(metrics.connected(sock.remoteAddress(), sock.remoteName()));
        }
        sock.registerEventBusHandler();
        connectHandler.complete(sock);
      });
    });
    handler.removeHandler(conn -> {
      socketMap.remove(ch);
    });
    ch.pipeline().addLast("handler", handler);
  }

  private void failed(ContextInternal context, Channel ch, Throwable th, Promise<NetSocket> connectHandler) {
    if (ch != null) {
      ch.close();
    }
    context.emitFromIO(th, connectHandler::tryFail);
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

