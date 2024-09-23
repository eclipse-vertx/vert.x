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
import io.vertx.core.http.HttpVersion;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.CloseSequence;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.internal.net.NetClientInternal;
import io.vertx.core.internal.tls.SslContextManager;
import io.vertx.core.internal.tls.SslContextProvider;
import io.vertx.core.net.*;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.TCPMetrics;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 *
 * This class is thread-safe
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
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
  private final SslContextManager sslContextManager;
  private volatile ClientSSLOptions sslOptions;
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
    this.channelGroup = new DefaultChannelGroup(vertx.getAcceptorEventLoopGroup().next(), true);
    this.options = new NetClientOptions(options);
    this.sslContextManager = new SslContextManager(SslContextManager.resolveEngineOptions(options.getSslEngineOptions(), options.isUseAlpn()));
    this.metrics = metrics;
    this.logEnabled = options.getLogActivity();
    this.idleTimeout = options.getIdleTimeout();
    this.readIdleTimeout = options.getReadIdleTimeout();
    this.writeIdleTimeout = options.getWriteIdleTimeout();
    this.idleTimeoutUnit = options.getIdleTimeoutUnit();
    this.closeSequence = closeSequence1;
    this.proxyFilter = options.getNonProxyHosts() != null ? ProxyFilter.nonProxyHosts(options.getNonProxyHosts()) : ProxyFilter.DEFAULT_PROXY_FILTER;
    this.sslOptions = options.getSslOptions();
  }

  protected void initChannel(ChannelPipeline pipeline, boolean ssl) {
    if (logEnabled) {
      pipeline.addLast("logging", new LoggingHandler(options.getActivityLogDataFormat()));
    }
    if (ssl || !vertx.transport().supportFileRegion()) {
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
    return connect(remoteAddress, null);
  }

  @Override
  public Future<NetSocket> connect(SocketAddress remoteAddress, String serverName) {
    ConnectOptions connectOptions = new ConnectOptions();
    connectOptions.setRemoteAddress(remoteAddress);
    String peerHost = remoteAddress.host();
    if (peerHost != null && peerHost.endsWith(".")) {
      peerHost= peerHost.substring(0, peerHost.length() - 1);
    }
    if (peerHost != null) {
      connectOptions.setHost(peerHost);
      connectOptions.setPort(remoteAddress.port());
    }
    connectOptions.setSsl(options.isSsl());
    connectOptions.setSniServerName(serverName);
    connectOptions.setSslOptions(sslOptions);
    return connect(connectOptions);
  }

  @Override
  public Future<NetSocket> connect(ConnectOptions connectOptions) {
    ContextInternal context = vertx.getOrCreateContext();
    Promise<NetSocket> promise = context.promise();
    connectInternal(connectOptions, options.isRegisterWriteHandler(), promise, context, options.getReconnectAttempts());
    return promise.future();
  }

  @Override
  public void connectInternal(ConnectOptions connectOptions, Promise<NetSocket> connectHandler, ContextInternal context) {
    ClientSSLOptions sslOptions = connectOptions.getSslOptions();
    if (sslOptions == null) {
      connectOptions.setSslOptions(this.sslOptions);
      if (connectOptions.getSslOptions() == null) {
        connectOptions.setSslOptions(new ClientSSLOptions()); // DO WE NEED THIS ??? AVOID NPE
      }
    }
    connectInternal(connectOptions, false, connectHandler, context, 0);
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
  public Future<Boolean> updateSSLOptions(ClientSSLOptions options, boolean force) {
    ContextInternal ctx = vertx.getOrCreateContext();
    synchronized (this) {
      this.sslOptions = options;
      this.sslOptions.setHttp3(this.options.getProtocolVersion() == HttpVersion.HTTP_3);
    }
    return ctx.succeededFuture(true);
  }

  private void connectInternal(ConnectOptions connectOptions,
                               boolean registerWriteHandlers,
                               Promise<NetSocket> connectHandler,
                               ContextInternal context,
                               int remainingAttempts) {
    if (closeSequence.started()) {
      connectHandler.fail(new IllegalStateException("Client is closed"));
    } else {
      if (connectOptions.isSsl()) {
        // We might be using an SslContext created from a plugged engine
        ClientSSLOptions sslOptions = connectOptions.getSslOptions() != null ? connectOptions.getSslOptions().copy() : this.sslOptions;
        if (sslOptions == null) {
          connectHandler.fail("ClientSSLOptions must be provided when connecting to a TLS server");
          return;
        }
        sslOptions.setHttp3(options.getProtocolVersion() == HttpVersion.HTTP_3);

        Future<SslContextProvider> fut;
        fut = sslContextManager.resolveSslContextProvider(
          sslOptions,
          sslOptions.getHostnameVerificationAlgorithm(),
          null,
          sslOptions.getApplicationLayerProtocols(),
          context);
        fut.onComplete(ar -> {
          if (ar.succeeded()) {
            connectInternal2(connectOptions, sslOptions, ar.result(), registerWriteHandlers, connectHandler, context, remainingAttempts);
          } else {
            connectHandler.fail(ar.cause());
          }
        });
      } else {
        connectInternal2(connectOptions, connectOptions.getSslOptions(), null, registerWriteHandlers, connectHandler, context, remainingAttempts);
      }
    }
  }

  private void connectInternal2(ConnectOptions connectOptions,
                                ClientSSLOptions sslOptions,
                                SslContextProvider sslContextProvider,
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

      SocketAddress remoteAddress = connectOptions.getRemoteAddress();
      if (remoteAddress == null) {
        String host = connectOptions.getHost();
        Integer port = connectOptions.getPort();
        if (host == null || port == null) {
          throw new UnsupportedOperationException("handle me");
        }
        remoteAddress = SocketAddress.inetSocketAddress(port, host);
      }

      SocketAddress peerAddress = peerAddress(remoteAddress, connectOptions);

      int connectTimeout = connectOptions.getTimeout();
      if (connectTimeout < 0) {
        connectTimeout = options.getConnectTimeout();
      }
      vertx.transport().configure(options, connectTimeout, remoteAddress.isDomainSocket(), bootstrap);

      ProxyOptions proxyOptions = connectOptions.getProxyOptions();
      if (proxyOptions == null) {
        proxyOptions = options.getProxyOptions();
      }
      if (proxyFilter != null) {
        if (!proxyFilter.test(remoteAddress)) {
          proxyOptions = null;
        }
      }

      ChannelProvider channelProvider = new ChannelProvider(bootstrap, sslContextProvider, context)
        .proxyOptions(proxyOptions).version(options.getProtocolVersion());;

      SocketAddress captured = remoteAddress;

      channelProvider.handler(ch -> connected(
        context,
        sslOptions,
        ch,
        connectHandler,
        captured,
        connectOptions.isSsl(),
        channelProvider.applicationProtocol(),
        registerWriteHandlers));
      io.netty.util.concurrent.Future<Channel> fut = channelProvider.connect(
        remoteAddress,
        peerAddress,
        connectOptions.getSniServerName(),
        connectOptions.isSsl(),
        sslOptions);
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
                connectInternal(
                  connectOptions,
                  registerWriteHandlers,
                  connectHandler,
                  context,
                  remainingAttempts == -1 ? remainingAttempts : remainingAttempts - 1)
              );
            });
          } else {
            failed(context, null, cause, connectHandler);
          }
        }
      });
    } else {
      eventLoop.execute(() -> connectInternal2(connectOptions, sslOptions, sslContextProvider, registerWriteHandlers, connectHandler, context, remainingAttempts));
    }
  }

  private static SocketAddress peerAddress(SocketAddress remoteAddress, ConnectOptions connectOptions) {
    if (!connectOptions.isSsl()) {
      return null;
    }
    String peerHost = connectOptions.getHost();
    Integer peerPort = connectOptions.getPort();
    if (remoteAddress.isInetSocket()) {
      if ((peerHost == null || peerHost.equals(remoteAddress.host()))
        && (peerPort == null || peerPort.intValue() == remoteAddress.port())) {
        return remoteAddress;
      }
      if (peerHost == null) {
        peerHost = remoteAddress.host();;
      }
      if (peerPort == null) {
        peerPort = remoteAddress.port();
      }
    }
    return peerHost != null && peerPort != null ? SocketAddress.inetSocketAddress(peerPort, peerHost) : null;
  }

  private void connected(ContextInternal context,
                         ClientSSLOptions sslOptions,
                         Channel ch,
                         Promise<NetSocket> connectHandler,
                         SocketAddress remoteAddress,
                         boolean ssl,
                         String applicationLayerProtocol,
                         boolean registerWriteHandlers) {
    channelGroup.add(ch);
    initChannel(ch.pipeline(), ssl);
    VertxHandler<NetSocketImpl> handler = VertxHandler.create(ctx -> new NetSocketImpl(
      context,
      ctx,
      remoteAddress,
      sslContextManager,
      sslOptions,
      metrics,
      applicationLayerProtocol,
      registerWriteHandlers));
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

