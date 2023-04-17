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
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.impl.PartialPooledByteBufAllocator;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.CloseSequence;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.*;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.TCPMetrics;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 *
 * This class is thread-safe
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class NetClientImpl implements NetClientInternal {

  private static final Logger log = LoggerFactory.getLogger(NetClientImpl.class);
  protected final int idleTimeout;
  protected final int readIdleTimeout;
  protected final int writeIdleTimeout;
  private final TimeUnit idleTimeoutUnit;
  protected final boolean logEnabled;

  private final VertxInternal vertx;
  private final NetClientOptions options;
  private final SSLHelper sslHelper;
  private final AtomicReference<Future<SslChannelProvider>> sslChannelProvider = new AtomicReference<>();
  public final ChannelGroup channelGroup;
  private final TCPMetrics metrics;
  public ShutdownEvent closeEvent;
  private ChannelGroupFuture graceFuture;
  private final CloseSequence closeSequence;
  private final Predicate<SocketAddress> proxyFilter;

  public NetClientImpl(VertxInternal vertx, TCPMetrics metrics, NetClientOptions options) {

    //
    // 3 steps close sequence
    // 2: a {@link CloseEvent} event is broadcast to each channel, channels should react accordingly
    // 1: grace period completed when all channels are inactive or the shutdown timeout is fired
    // 0: sockets are closed
    CloseSequence closeSequence1 = new CloseSequence(this::doClose, this::doGrace, this::doShutdown);

    this.vertx = vertx;
    this.channelGroup = new DefaultChannelGroup(vertx.getAcceptorEventLoopGroup().next());
    this.options = new NetClientOptions(options);
    this.sslHelper = new SSLHelper(options, options.getApplicationLayerProtocols());
    this.metrics = metrics;
    this.logEnabled = options.getLogActivity();
    this.idleTimeout = options.getIdleTimeout();
    this.readIdleTimeout = options.getReadIdleTimeout();
    this.writeIdleTimeout = options.getWriteIdleTimeout();
    this.idleTimeoutUnit = options.getIdleTimeoutUnit();
    this.closeSequence = closeSequence1;
    this.proxyFilter = options.getNonProxyHosts() != null ? ProxyFilter.nonProxyHosts(options.getNonProxyHosts()) : ProxyFilter.DEFAULT_PROXY_FILTER;
  }

  protected void initChannel(ChannelPipeline pipeline) {
    if (logEnabled) {
      pipeline.addLast("logging", new LoggingHandler(options.getActivityLogDataFormat()));
    }
    if (options.isSsl() || !vertx.transport().supportFileRegion()) {
      // only add ChunkedWriteHandler when SSL is enabled otherwise it is not needed as FileRegion is used.
      pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());       // For large file / sendfile support
    }
    if (idleTimeout > 0 || readIdleTimeout > 0 || writeIdleTimeout > 0) {
      pipeline.addLast("idle", new IdleStateHandler(readIdleTimeout, writeIdleTimeout, idleTimeout, idleTimeoutUnit));
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
    return connect(vertx.getOrCreateContext(), remoteAddress, serverName);
  }

  public Future<NetSocket> connect(ContextInternal context, SocketAddress remoteAddress, String serverName) {
    Promise<NetSocket> promise = context.promise();
    connect(remoteAddress, serverName, promise, context);
    return promise.future();
  }

  private void doShutdown(Promise<Void> p) {
    if (closeEvent == null) {
      closeEvent = new ShutdownEvent(0, TimeUnit.SECONDS);
    }
    graceFuture = channelGroup.newCloseFuture();
    for (Channel ch : channelGroup) {
      ch.pipeline().fireUserEventTriggered(closeEvent);
    }
    p.complete();
  }

  private void doGrace(Promise<Void> completion) {
    if (closeEvent.timeout() > 0L) {
      long timerID = vertx.setTimer(closeEvent.timeUnit().toMillis(closeEvent.timeout()), v -> {
        completion.complete();
      });
      graceFuture.addListener(future -> {
        if (vertx.cancelTimer(timerID)) {
          completion.complete();
        }
      });
    } else {
      completion.complete();
    }
  }

  private void doClose(Promise<Void> completion) {
    ChannelGroupFuture fut = channelGroup.close();
    if (metrics != null) {
      PromiseInternal<Void> p = (PromiseInternal) Promise.promise();
      fut.addListener(p);
      p.future().<Void>compose(v -> {
        metrics.close();
        return Future.succeededFuture();
      }).onComplete(completion);
    } else {
      fut.addListener((PromiseInternal)completion);
    }
  }

  @Override
  public void close(Promise<Void> completion) {
    closeSequence.close(completion);
  }

  @Override
  public Future<Void> closeFuture() {
    return closeSequence.future();
  }

  @Override
  public Future<Void> shutdown(long timeout, TimeUnit timeUnit) {
    closeEvent = new ShutdownEvent(timeout, timeUnit);
    return closeSequence.progressTo(1);
  }

  @Override
  public Future<Void> close(long timeout, TimeUnit timeUnit) {
    closeEvent = new ShutdownEvent(timeout, timeUnit);
    return closeSequence.close();
  }

  @Override
  public boolean isMetricsEnabled() {
    return metrics != null;
  }

  @Override
  public Metrics getMetrics() {
    return metrics;
  }

  @Override
  public Future<Void> updateSSLOptions(SSLOptions options) {
    Future<SslChannelProvider> fut = sslHelper.buildChannelProvider(new SSLOptions(options), vertx.getOrCreateContext());
    fut.onSuccess(v -> sslChannelProvider.set(fut));
    return fut.mapEmpty();
  }

  private void connect(SocketAddress remoteAddress, String serverName, Promise<NetSocket> connectHandler, ContextInternal ctx) {
    if (closeSequence.started()) {
      throw new IllegalStateException("Client is closed");
    }
    SocketAddress peerAddress = remoteAddress;
    String peerHost = peerAddress.host();
    if (peerHost != null && peerHost.endsWith(".")) {
      peerAddress = SocketAddress.inetSocketAddress(peerAddress.port(), peerHost.substring(0, peerHost.length() - 1));
    }
    ProxyOptions proxyOptions = options.getProxyOptions();
    if (proxyFilter != null) {
      if (!proxyFilter.test(remoteAddress)) {
        proxyOptions = null;
      }
    }
    connectInternal(proxyOptions, remoteAddress, peerAddress, serverName, options.isSsl(), options.isUseAlpn(), options.isRegisterWriteHandler(), connectHandler, ctx, options.getReconnectAttempts());
  }

  /**
   * Open a socket to the {@code remoteAddress} server.
   *
   * @param proxyOptions optional proxy configuration
   * @param remoteAddress the server address
   * @param peerAddress the peer address (along with SSL)
   * @param serverName the SNI server name (along with SSL)
   * @param ssl whether to use SSL
   * @param useAlpn wether to use ALPN (along with SSL)
   * @param registerWriteHandlers whether to register event-bus write handlers
   * @param connectHandler the promise to resolve with the connect result
   * @param context the socket context
   * @param remainingAttempts how many times reconnection is reattempted
   */
  public void connectInternal(ProxyOptions proxyOptions,
                                SocketAddress remoteAddress,
                                SocketAddress peerAddress,
                                String serverName,
                                boolean ssl,
                                boolean useAlpn,
                                boolean registerWriteHandlers,
                                Promise<NetSocket> connectHandler,
                                ContextInternal context,
                                int remainingAttempts) {
    if (closeSequence.started()) {
      connectHandler.fail(new IllegalStateException("Client is closed"));
    } else {
      Future<SslChannelProvider> fut;
      while (true) {
        fut = sslChannelProvider.get();
        if (fut == null) {
          fut = sslHelper.buildChannelProvider(options.getSslOptions(), context);
          if (sslChannelProvider.compareAndSet(null, fut)) {
            break;
          }
        } else {
          break;
        }
      }
      fut.onComplete(ar -> {
        if (ar.succeeded()) {
          connectInternal2(proxyOptions, remoteAddress, peerAddress, ar.result(), serverName, ssl, useAlpn, registerWriteHandlers, connectHandler, context, remainingAttempts);
        } else {
          connectHandler.fail(ar.cause());
        }
      });
    }
  }

  private void connectInternal2(ProxyOptions proxyOptions,
                              SocketAddress remoteAddress,
                              SocketAddress peerAddress,
                              SslChannelProvider sslChannelProvider,
                              String serverName,
                              boolean ssl,
                              boolean useAlpn,
                              boolean registerWriteHandlers,
                              Promise<NetSocket> connectHandler,
                              ContextInternal context,
                              int remainingAttempts) {
    EventLoop eventLoop = context.nettyEventLoop();

    if (eventLoop.inEventLoop()) {
      Objects.requireNonNull(connectHandler, "No null connectHandler accepted");
      Bootstrap bootstrap = new Bootstrap();
      bootstrap.group(eventLoop);
      bootstrap.option(ChannelOption.ALLOCATOR, PartialPooledByteBufAllocator.INSTANCE);

      vertx.transport().configure(options, remoteAddress.isDomainSocket(), bootstrap);

      ChannelProvider channelProvider = new ChannelProvider(bootstrap, sslChannelProvider, context)
        .proxyOptions(proxyOptions);

      channelProvider.handler(ch -> connected(context, ch, connectHandler, remoteAddress, sslChannelProvider, channelProvider.applicationProtocol(), registerWriteHandlers));

      io.netty.util.concurrent.Future<Channel> fut = channelProvider.connect(remoteAddress, peerAddress, serverName, ssl, useAlpn);
      fut.addListener((GenericFutureListener<io.netty.util.concurrent.Future<Channel>>) future -> {
        if (!future.isSuccess()) {
          Throwable cause = future.cause();
          // FileNotFoundException for domain sockets
          boolean connectError = cause instanceof ConnectException || cause instanceof FileNotFoundException;
          if (connectError && (remainingAttempts > 0 || remainingAttempts == -1)) {
            context.emit(v -> {
              log.debug("Failed to create connection. Will retry in " + options.getReconnectInterval() + " milliseconds");
              //Set a timer to retry connection
              vertx.setTimer(options.getReconnectInterval(), tid ->
                connectInternal(proxyOptions, remoteAddress, peerAddress, serverName, ssl, useAlpn, registerWriteHandlers, connectHandler, context, remainingAttempts == -1 ? remainingAttempts : remainingAttempts - 1)
              );
            });
          } else {
            failed(context, null, cause, connectHandler);
          }
        }
      });
    } else {
      eventLoop.execute(() -> connectInternal2(proxyOptions, remoteAddress, peerAddress, sslChannelProvider, serverName, ssl, useAlpn, registerWriteHandlers, connectHandler, context, remainingAttempts));
    }
  }

  private void connected(ContextInternal context, Channel ch, Promise<NetSocket> connectHandler, SocketAddress remoteAddress, SslChannelProvider sslChannelProvider, String applicationLayerProtocol, boolean registerWriteHandlers) {
    channelGroup.add(ch);
    initChannel(ch.pipeline());
    VertxHandler<NetSocketImpl> handler = VertxHandler.create(ctx -> new NetSocketImpl(context, ctx, remoteAddress, sslChannelProvider, metrics, applicationLayerProtocol, registerWriteHandlers));
    handler.removeHandler(NetSocketImpl::unregisterEventBusHandler);
    handler.addHandler(sock -> {
      if (metrics != null) {
        sock.metric(metrics.connected(sock.remoteAddress(), sock.remoteName()));
      }
      sock.registerEventBusHandler();
      connectHandler.complete(sock);
    });
    ch.pipeline().addLast("handler", handler);
  }

  private void failed(ContextInternal context, Channel ch, Throwable th, Promise<NetSocket> connectHandler) {
    if (ch != null) {
      ch.close();
    }
    context.emit(th, connectHandler::tryFail);
  }
}

