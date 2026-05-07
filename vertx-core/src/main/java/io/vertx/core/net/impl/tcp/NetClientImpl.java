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

package io.vertx.core.net.impl.tcp;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.Channel;
import io.netty.handler.logging.ByteBufFormat;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.impl.CleanableResource;
import io.vertx.core.impl.Utils;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.impl.buffer.VertxByteBufAllocator;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.internal.net.NetClientInternal;
import io.vertx.core.internal.tls.ClientSslContextManager;
import io.vertx.core.internal.tls.ClientSslContextProvider;
import io.vertx.core.internal.tls.SslContextManager;
import io.vertx.core.internal.tls.SslContextProvider;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.ConnectRetry;
import io.vertx.core.net.impl.ConnectionGroup;
import io.vertx.core.net.TcpClientConfig;
import io.vertx.core.net.impl.ProxyFilter;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.TransportMetrics;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class NetClientImpl implements NetClientInternal, CleanableResource<NetClientInternal> {

  private static final Logger log = LoggerFactory.getLogger(NetClientImpl.class);
  protected final Duration idleTimeout;
  protected final Duration readIdleTimeout;
  protected final Duration writeIdleTimeout;
  protected final ByteBufFormat logging;

  private final VertxInternal vertx;
  private final TcpClientConfig config;
  private final TcpConfig transportOptions;
  private final String protocol;
  private final boolean registerWriteHandler;
  private final ClientSslContextManager sslContextManager;
  private volatile ClientSSLOptions sslOptions;
  public final ConnectionGroup channelGroup;
  private final TransportMetrics metrics;
  private final Predicate<SocketAddress> proxyFilter;

  public NetClientImpl(VertxInternal vertx,
                       TcpClientConfig config,
                       String protocol,
                       ClientSSLOptions sslOptions,
                       SSLEngineOptions sslEngineOptions,
                       boolean registerWriteHandler) {

    this.vertx = vertx;
    this.channelGroup = new ConnectionGroup(vertx.acceptorEventLoopGroup().next()) {
      @Override
      protected void handleClose(Completable<Void> completion) {
        NetClientImpl.this.handleClose(completion);
      }
    };
    LogConfig logConfig = config.getLogConfig();
    this.config = config;
    this.registerWriteHandler = registerWriteHandler;
    this.sslContextManager = new ClientSslContextManager(SslContextManager.resolveEngineOptions(sslEngineOptions, sslOptions != null && sslOptions.isUseAlpn()));
    this.metrics = vertx.metrics() != null ? vertx.metrics().createTcpClientMetrics(config, protocol) : null;
    this.logging = logConfig != null && logConfig.isEnabled() ? logConfig.getDataFormat() : null;
    this.idleTimeout = config.getIdleTimeout() != null ? config.getIdleTimeout() : Duration.ofMillis(0L);
    this.readIdleTimeout = config.getReadIdleTimeout() != null ? config.getReadIdleTimeout() : Duration.ofMillis(0L);
    this.writeIdleTimeout = config.getWriteIdleTimeout() != null ? config.getWriteIdleTimeout() : Duration.ofMillis(0L);
    this.proxyFilter = config.getNonProxyHosts() != null ? ProxyFilter.nonProxyHosts(config.getNonProxyHosts()) : ProxyFilter.DEFAULT_PROXY_FILTER;
    this.sslOptions = sslOptions;
    this.transportOptions = config.getTransportConfig();
    this.protocol = protocol;
  }

  protected void initChannel(ChannelPipeline pipeline, boolean ssl) {
    if (logging != null) {
      pipeline.addLast("logging", new LoggingHandler(logging));
    }
    if (ssl || !vertx.transport().supportFileRegion()) {
      // only add ChunkedWriteHandler when SSL is enabled otherwise it is not needed as FileRegion is used.
      pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());       // For large file / sendfile support
    }
    if (idleTimeout.toMillis() > 0 || readIdleTimeout.toMillis() > 0 || writeIdleTimeout.toMillis() > 0) {
      pipeline.addLast("idle", new IdleStateHandler(readIdleTimeout.toMillis(), writeIdleTimeout.toMillis(), idleTimeout.toMillis(), TimeUnit.MILLISECONDS));
    }
  }

  @Override
  public NetClientInternal get() {
    return this;
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
    connectOptions.setSsl(config.isSsl());
    connectOptions.setSniServerName(serverName);
    connectOptions.setSslOptions(sslOptions);
    return connect(connectOptions);
  }

  @Override
  public Future<NetSocket> connect(ConnectOptions connectOptions) {
    ContextInternal context = vertx.getOrCreateContext();
    Promise<NetSocket> promise = context.promise();
    connectInternal(connectOptions, registerWriteHandler, promise, context, config.getReconnectAttempts());
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

  private void handleClose(Completable<Void> completion) {
    try {
      if (metrics != null) {
        metrics.close();
      }
    } catch (Exception ignore) {
      //
    } finally {
      completion.succeed();
    }
  }

  @Override
  public void close(Completable<Void> completion) {
    channelGroup.shutdown(0, TimeUnit.SECONDS).onComplete(completion);
  }

  @Override
  public Future<Void> closeFuture() {
    return channelGroup.closeFuture();
  }

  @Override
  public Future<Void> shutdown(Duration timeout) {
    return channelGroup.shutdown(timeout);
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
    }
    return ctx.succeededFuture(true);
  }

  private void connectInternal(ConnectOptions connectOptions,
                               boolean registerWriteHandlers,
                               Promise<NetSocket> connectHandler,
                               ContextInternal context,
                               int remainingAttempts) {
    if (channelGroup.isStarted()) {
      connectHandler.fail(new IllegalStateException("Client is closed"));
    } else {
      if (connectOptions.isSsl()) {
        // We might be using an SslContext created from a plugged engine
        ClientSSLOptions sslOptions = connectOptions.getSslOptions() != null ? connectOptions.getSslOptions().copy() : this.sslOptions;
        if (sslOptions == null) {
          connectHandler.fail("ClientSSLOptions must be provided when connecting to a TLS server");
        } else if (sslOptions.getHostnameVerificationAlgorithm() == null) {
          connectHandler.fail("Missing hostname verification algorithm");
        } else {
          Future<ClientSslContextProvider> fut;
          fut = sslContextManager.resolveSslContextProvider(sslOptions, context);
          fut.onComplete(ar -> {
            if (ar.succeeded()) {
              connectInternal_(connectOptions, sslOptions, ar.result(), registerWriteHandlers, connectHandler, context, remainingAttempts);
            } else {
              connectHandler.fail(ar.cause());
            }
          });
        }
      } else {
        connectInternal_(connectOptions, connectOptions.getSslOptions(), null, registerWriteHandlers, connectHandler, context, remainingAttempts);
      }
    }
  }

  private void connectInternal_(ConnectOptions connectOptions,
                                ClientSSLOptions sslOptions,
                                SslContextProvider sslContextProvider,
                                boolean registerWriteHandlers,
                                Promise<NetSocket> connectHandler,
                                ContextInternal context,
                                int remainingAttempts) {
    Supplier<Future<NetSocket>> supplier = () -> connectInternal(connectOptions, sslOptions, sslContextProvider, registerWriteHandlers, context);
    ConnectRetry.connectWithRetries(log, supplier, context, connectHandler, config.getReconnectInterval(), remainingAttempts);
  }

    private Future<NetSocket> connectInternal(ConnectOptions connectOptions,
                                              ClientSSLOptions sslOptions,
                                              SslContextProvider sslContextProvider,
                                              boolean registerWriteHandlers,
                                              ContextInternal context) {
    PromiseInternal<NetSocket> p = context.promise();
    connectInternal(connectOptions, sslOptions, sslContextProvider, registerWriteHandlers, p, context);
    return p.future();
  }

  private void connectInternal(ConnectOptions connectOptions,
                               ClientSSLOptions sslOptions,
                               SslContextProvider sslContextProvider,
                               boolean registerWriteHandlers,
                               Promise<NetSocket> connectHandler,
                               ContextInternal context) {
    EventLoop eventLoop = context.nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      Objects.requireNonNull(connectHandler, "No null connectHandler accepted");
      Bootstrap bootstrap = new Bootstrap();
      bootstrap.group(eventLoop);
      bootstrap.option(ChannelOption.ALLOCATOR, VertxByteBufAllocator.POOLED_ALLOCATOR);

      SocketAddress remoteAddress = connectOptions.getRemoteAddress();
      if (remoteAddress == null) {
        String host = connectOptions.getHost();
        Integer port = connectOptions.getPort();
        if (host == null || port == null) {
          throw new UnsupportedOperationException("handle me");
        }
        remoteAddress = SocketAddress.inetSocketAddress(port, host);
      }
      HostAndPort peerAddress;
      String serverName;
      if (connectOptions.isSsl()) {
        if (remoteAddress.isInetSocket()) {
          peerAddress = Utils.peerAddress(remoteAddress, connectOptions.getHost(), connectOptions.getPort());
        } else {
          String peerHost = connectOptions.getHost();
          Integer peerPort = connectOptions.getPort();
          if (peerHost != null && peerPort != null) {
            peerAddress = HostAndPort.create(peerHost, peerPort);
          } else {
            peerAddress = null;
          }
        }
        serverName = connectOptions.getSniServerName();
      } else {
        peerAddress = null;
        serverName = null;
      }

      int connectTimeout = connectOptions.getTimeout();
      if (connectTimeout < 0) {
        connectTimeout = (int) config.getConnectTimeout().toMillis();
      }
      boolean domainSocket = remoteAddress.isDomainSocket();

      // Transport specific TCP configuration
      vertx.transport().configure(config.getTransportConfig(), domainSocket, bootstrap);

      SocketAddress localAddress = config.getLocalAddress();
      if (localAddress != null) {
        bootstrap.localAddress(localAddress.host(), localAddress.port());
      }

      //
      if (transportOptions.getSendBufferSize() != -1) {
        bootstrap.option(ChannelOption.SO_SNDBUF, transportOptions.getSendBufferSize());
      }
      if (!domainSocket) {
        bootstrap.option(ChannelOption.SO_REUSEADDR, transportOptions.isReuseAddress());
      }
      if (transportOptions.getTrafficClass() != -1) {
        bootstrap.option(ChannelOption.IP_TOS, transportOptions.getTrafficClass());
      }
      if (transportOptions.getReceiveBufferSize() != -1) {
        bootstrap.option(ChannelOption.SO_RCVBUF, transportOptions.getReceiveBufferSize());
        bootstrap.option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(transportOptions.getReceiveBufferSize()));
      }
      bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout);

      ProxyOptions proxyOptions = connectOptions.getProxyOptions();
      if (proxyOptions == null) {
        proxyOptions = config.getProxyOptions();
      }
      if (proxyFilter != null) {
        if (!proxyFilter.test(remoteAddress)) {
          proxyOptions = null;
        }
      }

      ChannelProvider channelProvider = new ChannelProvider(bootstrap, sslContextProvider, context)
        .proxyOptions(proxyOptions);

      SocketAddress captured = remoteAddress;

      io.netty.util.concurrent.Future<Channel> fut = channelProvider.connect(
        remoteAddress,
        peerAddress,
        serverName,
        connectOptions.isSsl(),
        sslOptions);
      fut.addListener((GenericFutureListener<io.netty.util.concurrent.Future<Channel>>) future -> {
        if (future.isSuccess()) {
          connected(
            context,
            sslOptions,
            future.getNow(),
            connectHandler,
            captured,
            connectOptions.isSsl(),
            registerWriteHandlers);
        } else {
          failed(context, null, future.cause(), connectHandler);
        }
      });
    } else {
      eventLoop.execute(() -> connectInternal(connectOptions, sslOptions, sslContextProvider, registerWriteHandlers, connectHandler, context));
    }
  }

  private void connected(ContextInternal context,
                         ClientSSLOptions sslOptions,
                         Channel ch,
                         Promise<NetSocket> connectHandler,
                         SocketAddress remoteAddress,
                         boolean ssl,
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

