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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.*;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.impl.buffer.VertxByteBufAllocator;
import io.vertx.core.internal.Closeable;
import io.vertx.core.internal.CloseableResource;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.internal.net.NetServerInternal;
import io.vertx.core.internal.net.SslChannelProvider;
import io.vertx.core.net.impl.SslEngineUtils;
import io.vertx.core.internal.net.SslHandshakeCompletionHandler;
import io.vertx.core.internal.resolver.NameResolver;
import io.vertx.core.internal.tls.ServerSslContextManager;
import io.vertx.core.internal.tls.ServerSslContextProvider;
import io.vertx.core.internal.tls.SslContextManager;
import io.vertx.core.internal.tls.SslContextProvider;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.*;
import io.vertx.core.spi.metrics.TransportMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.core.spi.transport.Transport;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Vert.x TCP server
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class NetServerImpl implements NetServerInternal {

  private static final Logger log = LoggerFactory.getLogger(NetServerImpl.class);

  private final VertxInternal vertx;
  private final TcpServerConfig config;
  private final SSLEngineOptions sslEngineOptions;
  private final ServerSSLOptions sslOptions;
  private final boolean fileRegionEnabled;
  private final boolean registerWriteHandler;
  private final String protocol;
  private Handler<NetSocket> handler;
  private Handler<Throwable> exceptionHandler;

  // Per server
  private volatile ChannelHandler channelHandler;
  private volatile CloseableResource<TcpServer> resource;

  public NetServerImpl(VertxInternal vertx,
                       TcpServerConfig config,
                       String protocol,
                       ServerSSLOptions sslOptions,
                       SSLEngineOptions sslEngineOptions,
                       boolean fileRegionEnabled,
                       boolean registerWriteHandler) {

    if (sslOptions == null) {
      sslOptions = new ServerSSLOptions();
    }

    this.vertx = vertx;
    this.config = config;
    this.protocol = protocol;
    this.fileRegionEnabled = fileRegionEnabled;
    this.registerWriteHandler = registerWriteHandler;
    this.sslOptions = sslOptions;
    this.sslEngineOptions = sslEngineOptions;
  }

  public ServerSslContextProvider sslContextProvider() {
    CloseableResource<TcpServer> ref = resource;
    return ref == null ? null : ref.get().sslContextProviderRef.get();
  }

  @Override
  public synchronized Handler<NetSocket> connectHandler() {
    return handler;
  }

  @Override
  public synchronized NetServerImpl connectHandler(Handler<NetSocket> handler) {
    if (isListening()) {
      throw new IllegalStateException("Cannot set connectHandler when server is listening");
    }
    this.handler = handler;
    return this;
  }

  @Override
  public synchronized NetServerImpl exceptionHandler(Handler<Throwable> handler) {
    if (isListening()) {
      throw new IllegalStateException("Cannot set exceptionHandler when server is listening");
    }
    this.exceptionHandler = handler;
    return this;
  }

  public int actualPort() {
    CloseableResource<TcpServer> ref = resource;
    return ref == null ? 0 : ref.get().actualPort;
  }

  @Override
  public Future<Void> shutdown(Duration timeout) {
    ChannelHandler handler = channelHandler;
    if (handler == null) {
      return vertx.getOrCreateContext().succeededFuture();
    }
    return handler.group.shutdown(timeout);
  }

  @Override
  public Future<NetServer> listen(SocketAddress localAddress) {
    return listen(vertx.getOrCreateContext(), localAddress);
  }

  public Future<NetServer> listen(ContextInternal context, SocketAddress localAddress) {
    if (localAddress == null) {
      throw new NullPointerException("No null bind local address");
    }
    if (handler == null) {
      throw new IllegalStateException("Set connect handler first");
    }
    return bind(context, localAddress).map(this);
  }

  @Override
  public Future<NetServer> listen() {
    return listen(config.getPort(), config.getHost());
  }

  public boolean isClosed() {
    return !isListening();
  }

  private static class TcpServer implements Closeable {

    private final SslContextManager<?> sslContextManager;
    private final List<String> resolvedKeyExchangeGroups;
    private final SslContextProviderReference sslContextProviderRef;
    private final GlobalTrafficShapingHandler trafficShapingHandler;
    private final ServerChannelLoadBalancer channelBalancer;
    private final Future<Channel> bindFuture;
    private TransportMetrics<?> metrics;
    private volatile int actualPort;

    public TcpServer(SSLEngineOptions sslEngineOptions,
                     ServerSSLOptions sslOptions,
                     Future<Channel> promise,
                     EventLoopGroup eventLoopGroup,
                     TrafficShapingOptions trafficShapingOptions,
                     int actualPort) {

      ServerSslContextManager resolvedKeyEG = new ServerSslContextManager(SslContextManager.resolveEngineOptions(sslEngineOptions, sslOptions.isUseAlpn(), sslOptions.getPqcEnforcementPolicy()));
      List<String> resolvedKeyExchangeGroups = SslEngineUtils.resolveKeyExchangeGroups(sslOptions.getKeyExchangeGroups(), sslOptions.getPqcEnforcementPolicy());

      this.resolvedKeyExchangeGroups = resolvedKeyExchangeGroups;
      this.sslContextProviderRef = new SslContextProviderReference(resolvedKeyEG);
      this.bindFuture = promise;
      this.sslContextManager = resolvedKeyEG;
      this.trafficShapingHandler = createTrafficShapingHandler(eventLoopGroup, trafficShapingOptions);
      this.channelBalancer = new ServerChannelLoadBalancer();
      this.actualPort = actualPort;
    }

    private static GlobalTrafficShapingHandler createTrafficShapingHandler(EventLoopGroup eventLoopGroup, TrafficShapingOptions options) {
      if (options == null) {
        return null;
      }
      GlobalTrafficShapingHandler trafficShapingHandler;
      if (options.getMaxDelayToWait() != 0) {
        long maxDelayToWaitInMillis = options.getMaxDelayToWaitTimeUnit().toMillis(options.getMaxDelayToWait());
        long checkIntervalForStatsInMillis = options.getCheckIntervalForStatsTimeUnit().toMillis(options.getCheckIntervalForStats());
        trafficShapingHandler = new GlobalTrafficShapingHandler(eventLoopGroup, options.getOutboundGlobalBandwidth(), options.getInboundGlobalBandwidth(), checkIntervalForStatsInMillis, maxDelayToWaitInMillis);
      } else {
        long checkIntervalForStatsInMillis = options.getCheckIntervalForStatsTimeUnit().toMillis(options.getCheckIntervalForStats());
        trafficShapingHandler = new GlobalTrafficShapingHandler(eventLoopGroup, options.getOutboundGlobalBandwidth(), options.getInboundGlobalBandwidth(), checkIntervalForStatsInMillis);
      }
      if (options.getPeakOutboundGlobalBandwidth() != 0) {
        trafficShapingHandler.setMaxGlobalWriteSize(options.getPeakOutboundGlobalBandwidth());
      }
      return trafficShapingHandler;
    }

    private void bind(
      TcpServerConfig config,
      ServerSSLOptions sslOptions,
      EventLoopGroup acceptorGroup,
      String protocol,
      VertxMetrics vertxMetrics,
      Transport transport,
      String hostOrPath,
      ContextInternal context,
      SocketAddress bindAddress,
      SocketAddress localAddress) {
      PromiseInternal<Channel> bindPromise = (PromiseInternal<Channel>) bindFuture;
      // Initialize SSL before binding
      if (config.isSsl()) {
        sslContextProviderRef
          .update(sslOptions, context)
          .onComplete(ar -> {
            if (ar.succeeded()) {
              bind2(config, acceptorGroup, protocol, vertxMetrics, transport, hostOrPath, context, bindAddress, localAddress, bindPromise);
            } else {
              bindPromise.fail(ar.cause());
            }
          });
      } else {
        bind2(config, acceptorGroup, protocol, vertxMetrics, transport, hostOrPath, context, bindAddress, localAddress, bindPromise);
      }
    }

    private void bind2(
      TcpServerConfig config,
      EventLoopGroup acceptorGroup,
      String protocol,
      VertxMetrics vertxMetrics,
      Transport transport,
      String hostOrPath,
      ContextInternal context,
      SocketAddress bindAddress,
      SocketAddress localAddress,
      Promise<Channel> promise) {

      ServerBootstrap bootstrap = new ServerBootstrap();
      bootstrap.group(acceptorGroup, channelBalancer.workers());
      bootstrap.childHandler(channelBalancer);
      bootstrap.childOption(ChannelOption.ALLOCATOR, VertxByteBufAllocator.POOLED_ALLOCATOR);
      applyConnectionOptions(localAddress.isDomainSocket(), bootstrap, config, transport);

      // Actual bind
      io.netty.util.concurrent.Future<Channel> bindFuture = resolveAndBind(context, bindAddress, bootstrap);
      bindFuture.addListener((GenericFutureListener<io.netty.util.concurrent.Future<Channel>>) res -> {
        if (res.isSuccess()) {
          Channel ch = res.getNow();
          log.trace("Net server listening on " + hostOrPath + ":" + ch.localAddress());
          // Update port to actual port when it is not a domain socket as wildcard port 0 might have been used
          if (bindAddress.isInetSocket()) {
            actualPort = ((InetSocketAddress)ch.localAddress()).getPort();
          }
          metrics = createMetrics(vertxMetrics, localAddress, config, protocol);
          promise.complete(ch);
        } else {
          promise.fail(res.cause());
        }
      });
    }

    private TransportMetrics<?> createMetrics(VertxMetrics metrics, SocketAddress localAddress, TcpServerConfig config, String protocol) {
      return metrics != null ? metrics.createTcpServerMetrics(config, protocol, localAddress) : null;
    }

    /**
     * Apply the connection option to the server.
     *
     * @param domainSocket whether it's a domain socket server
     * @param bootstrap the Netty server bootstrap
     */
    private void applyConnectionOptions(boolean domainSocket, ServerBootstrap bootstrap, TcpServerConfig config, Transport transport) {

      // Server socket channel
      if (config.getAcceptBacklog() != -1) {
        bootstrap.option(ChannelOption.SO_BACKLOG, config.getAcceptBacklog());
      }

      TcpConfig transportOptions = config.getTransportConfig();

      //  Socket/Datagram channel
      if (transportOptions.getSendBufferSize() != -1) {
        bootstrap.childOption(ChannelOption.SO_SNDBUF, transportOptions.getSendBufferSize());
      }
      if (!domainSocket) {
        bootstrap.option(ChannelOption.SO_REUSEADDR, transportOptions.isReuseAddress());
      }
      if (transportOptions.getTrafficClass() != -1) {
        bootstrap.childOption(ChannelOption.IP_TOS, transportOptions.getTrafficClass());
      }

      // Channel
      if (transportOptions.getReceiveBufferSize() != -1) {
        bootstrap.childOption(ChannelOption.SO_RCVBUF, transportOptions.getReceiveBufferSize());
        bootstrap.childOption(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(transportOptions.getReceiveBufferSize()));
      }

      transport.configure(config.getTransportConfig(), domainSocket, bootstrap);
    }

    @Override
    public Future<Void> shutdown(Duration timeout) {

      Promise<Void> done = Promise.promise(); // Use context???

      bindFuture.onComplete(ar -> {
        if (ar.succeeded()) {
          Channel channel = ar.result();
          ChannelFuture a = channel.close();
          if (metrics != null) {
            a.addListener(cg -> metrics.close());
          }
          a.addListener((PromiseInternal<Void>)done);
        } else {
          done.succeed();
        }
      });

      return done.future();
    }
  }

  private class ChannelHandler implements Handler<Channel> {

    private final TcpServer server;
    private final EventLoop eventLoop;
    private final ConnectionGroup group;
    private final ContextInternal context;
    private final Handler<NetSocket> connectionHandler;
    private final Handler<Throwable> exceptionHandler;
    private final GlobalTrafficShapingHandler trafficShapingHandler;

    ChannelHandler(TcpServer server,
                   EventLoop eventLoop,
                   ConnectionGroup group,
                   ContextInternal context,
                   Handler<NetSocket> connectionHandler,
                   Handler<Throwable> exceptionHandler,
                   GlobalTrafficShapingHandler trafficShapingHandler) {
      this.server = server;
      this.eventLoop = eventLoop;
      this.group = group;
      this.context = context;
      this.connectionHandler = connectionHandler;
      this.exceptionHandler = exceptionHandler;
      this.trafficShapingHandler = trafficShapingHandler;
    }

    void init() {
      server.channelBalancer.addWorker(eventLoop, this);
    }

    @Override
    public void handle(Channel ch) {
      group.add(ch);
      channelHandler.accept(ch, server.sslContextProviderRef.get(), server.sslContextManager, sslOptions, server.resolvedKeyExchangeGroups);
    }

    protected boolean accept() {
      return true;
    }

    public void accept(Channel ch, SslContextProvider sslChannelProvider, SslContextManager<?> sslContextManager,
                       ServerSSLOptions sslOptions, List<String> resolvedKeyExchangeGroups) {
      if (!this.accept()) {
        ch.close();
        return;
      }
      if (HAProxyMessageCompletionHandler.canUseProxyProtocol(config.isUseProxyProtocol())) {
        IdleStateHandler idle;
        io.netty.util.concurrent.Promise<Channel> p = ch.eventLoop().newPromise();
        ch.pipeline().addLast(new HAProxyMessageDecoder());
        Duration proxyProtocolTimeout = config.getProxyProtocolTimeout();
        if (!(proxyProtocolTimeout.isNegative() || proxyProtocolTimeout.isZero())) {
          ch.pipeline().addLast("idle", idle = new IdleStateHandler(0, 0, proxyProtocolTimeout.toMillis(), TimeUnit.MILLISECONDS));
        } else {
          idle = null;
        }
        ch.pipeline().addLast(new HAProxyMessageCompletionHandler(p));
        p.addListener((GenericFutureListener<io.netty.util.concurrent.Future<Channel>>) future -> {
          if (future.isSuccess()) {
            if (idle != null) {
              ch.pipeline().remove(idle);
            }
            configurePipeline(future.getNow(), sslChannelProvider, sslContextManager, sslOptions, resolvedKeyExchangeGroups);
          } else {
            //No need to close the channel.HAProxyMessageDecoder already did
            handleException(future.cause());
          }
        });
      } else {
        configurePipeline(ch, sslChannelProvider, sslContextManager, sslOptions, resolvedKeyExchangeGroups);
      }
    }

    private void configurePipeline(Channel ch, SslContextProvider sslContextProvider, SslContextManager<?> sslContextManager,
                                   ServerSSLOptions sslOptions, List<String> resolvedKeyExchangeGroups) {
      if (config.isSsl()) {
        List<String> applicationProtocols;
        if (sslOptions.isUseAlpn()) {
          applicationProtocols = sslOptions.getApplicationLayerProtocols();
        } else {
          applicationProtocols = null;
        }
        SslChannelProvider sslChannelProvider = new SslChannelProvider(vertx, sslContextProvider, sslOptions.isSni(), resolvedKeyExchangeGroups);
        ch.pipeline().addLast("ssl", sslChannelProvider.createServerHandler(applicationProtocols, sslOptions.getSslHandshakeTimeout(),
          sslOptions.getSslHandshakeTimeoutUnit(), HttpUtils.socketAddressToHostAndPort(ch.remoteAddress())));
        ChannelPromise p = ch.newPromise();
        ch.pipeline().addLast("handshaker", new SslHandshakeCompletionHandler(p));
        p.addListener(future -> {
          if (future.isSuccess()) {
            connected(ch, sslContextManager, sslOptions);
          } else {
            handleException(future.cause());
          }
        });
      } else {
        connected(ch, sslContextManager, sslOptions);
      }
      if (trafficShapingHandler != null) {
        ch.pipeline().addFirst("globalTrafficShaping", trafficShapingHandler);
      }
    }

    private void handleException(Throwable cause) {
      if (exceptionHandler != null) {
        context.emit(v -> exceptionHandler.handle(cause));
      }
    }

    private void connected(Channel ch, SslContextManager<?> sslContextManager, SSLOptions sslOptions) {
      initChannel(ch.pipeline(), config.isSsl());
      TransportMetrics<?> metrics = getMetrics();
      VertxHandler<NetSocketImpl> handler = VertxHandler.create(ctx -> new NetSocketImpl(context, ctx, sslContextManager, sslOptions, metrics, registerWriteHandler));
      handler.removeHandler(NetSocketImpl::unregisterEventBusHandler);
      handler.addHandler(conn -> {
        if (metrics != null) {
          conn.metric(metrics.connected(conn.remoteAddress(), conn.remoteName()));
        }
        conn.registerEventBusHandler();
        context.emit(conn, connectionHandler::handle);
      });
      ch.pipeline().addLast("handler", handler);
    }
  }

  protected void initChannel(ChannelPipeline pipeline, boolean ssl) {
    LogConfig logConfig = config.getLogConfig();
    if (logConfig != null && logConfig.isEnabled()) {
      pipeline.addLast("logging", new LoggingHandler(logConfig.getDataFormat()));
    }
    long idleTimeout = config.getIdleTimeout() != null ? config.getIdleTimeout().toMillis() : 0L;
    long readIdleTimeout = config.getReadIdleTimeout() != null ? config.getReadIdleTimeout().toMillis() : 0L;
    long writeIdleTimeout = config.getWriteIdleTimeout() != null ? config.getWriteIdleTimeout().toMillis() : 0L;
    if (idleTimeout > 0 || readIdleTimeout > 0 || writeIdleTimeout > 0) {
      pipeline.addLast("idle", new IdleStateHandler(readIdleTimeout, writeIdleTimeout, idleTimeout, TimeUnit.MILLISECONDS));
    }
    if (ssl || !fileRegionEnabled || !vertx.transport().supportFileRegion() || (config.getTrafficShapingOptions() != null && config.getTrafficShapingOptions().getOutboundGlobalBandwidth() > 0)) {
      // only add ChunkedWriteHandler when SSL is enabled or FileRegion isn't supported or when outbound traffic shaping is enabled
      pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());       // For large file / sendfile support
    }
  }

  public int sniEntrySize() {
    CloseableResource<TcpServer> ref = resource;
    return ref == null ? 0 : ref.get().sslContextManager.sniEntrySize();
  }

  public Future<Boolean> updateSSLOptions(ServerSSLOptions options, boolean force) {
    ContextInternal ctx = vertx.getOrCreateContext();
    return resource
      .get()
      .sslContextProviderRef
      .update(options, ctx, force)
      .map(Objects::nonNull);
  }

  public Future<Boolean> updateTrafficShapingOptions(TrafficShapingOptions options) {
    if (options == null) {
      throw new IllegalArgumentException("Invalid null value passed for traffic shaping options update");
    }
    TcpServer server = this.resource.get();
    ContextInternal ctx = vertx.getOrCreateContext();
    if (server == null) {
      // Server not yet started
      TrafficShapingOptions prev = this.config.getTrafficShapingOptions();
      boolean updated = prev == null || !prev.equals(options);
      this.config.setTrafficShapingOptions(options);
      return ctx.succeededFuture(updated);
    }
    // Update the traffic shaping options only for the actual/main server
    Promise<Boolean> promise = ctx.promise();
    ctx.emit(v -> updateTrafficShapingOptions(this.resource.get(), options, promise));
    return promise.future();
  }

  private void updateTrafficShapingOptions(TcpServer server, TrafficShapingOptions options, Promise<Boolean> promise) {
    if (server.trafficShapingHandler == null) {
      promise.fail(new IllegalStateException("Unable to update traffic shaping options because the server was not configured " +
        "to use traffic shaping during startup"));
    } else if (!options.equals(this.config.getTrafficShapingOptions())) {
      // Compare with existing traffic-shaping options to ensure they are updated only when they differ.
      this.config.setTrafficShapingOptions(options);
      long checkIntervalForStatsInMillis = options.getCheckIntervalForStatsTimeUnit().toMillis(options.getCheckIntervalForStats());
      server.trafficShapingHandler.configure(options.getOutboundGlobalBandwidth(), options.getInboundGlobalBandwidth(), checkIntervalForStatsInMillis);
      if (options.getPeakOutboundGlobalBandwidth() != 0) {
        server.trafficShapingHandler.setMaxGlobalWriteSize(options.getPeakOutboundGlobalBandwidth());
      }
      if (options.getMaxDelayToWait() != 0) {
        long maxDelayToWaitInMillis = options.getMaxDelayToWaitTimeUnit().toMillis(options.getMaxDelayToWait());
        server.trafficShapingHandler.setMaxWriteDelay(maxDelayToWaitInMillis);
      }
      promise.complete(true);
    } else {
      log.info("Not updating traffic shaping options as they have not changed");
      promise.complete(false);
    }
  }

  private synchronized Future<Channel> bind(ContextInternal context, SocketAddress localAddress) {
    if (channelHandler != null) {
      throw new IllegalStateException("Listen already called");
    }
    if (config.isSsl() && sslOptions.getKeyCertOptions() == null && sslOptions.getTrustOptions() == null) {
      return context.failedFuture("Key/certificate is mandatory for SSL");
    }

    String hostOrPath = localAddress.isInetSocket() ? localAddress.host() : localAddress.path();
    boolean shared;
    ServerID id;
    SocketAddress bindAddress;
    int ap = localAddress.port();
    if (ap > 0 || localAddress.isDomainSocket()) {
      id = new ServerID(ap, hostOrPath);
      shared = true;
      bindAddress = localAddress;
    } else {
      if (ap < 0) {
        id = new ServerID(ap, hostOrPath + "/" + -ap);
        shared = true;
        bindAddress = SocketAddress.inetSocketAddress(0, localAddress.host());
      } else {
        id = new ServerID(ap, hostOrPath);
        shared = false;
        bindAddress = localAddress;
      }
    }

    ConnectionGroup group = new ConnectionGroup(context.nettyEventLoop()) {
      @Override
      protected void handleClose(Completable<Void> completion) {
        NetServerImpl.this.handleClose(completion);
      }
      @Override
      protected void handleShutdown(Duration timeout, Completable<Void> completion) {
        NetServerImpl.this.handleShutdown(completion);
      }
    };

    CloseableResource<TcpServer> resource;
    if (shared) {
      String key = id.host() + "." + id.port();
      boolean[] created = new boolean[1]; // Hack
      resource = vertx.createSharedResource("__vertx.shared.tcpServers", key, () -> {
        created[0] = true;
        return new TcpServer(sslEngineOptions, sslOptions, context.promise(), vertx.eventLoopGroup(), config.getTrafficShapingOptions(), ap);
      });
      if (created[0]) {
        resource.get().bind(config, sslOptions, vertx.acceptorEventLoopGroup(), protocol, vertx.metrics(), vertx.transport(), hostOrPath, context, bindAddress, localAddress);
      }
    } else {
      PromiseInternal<Channel> promise = context.promise();
      TcpServer server = new TcpServer(sslEngineOptions, sslOptions, promise, vertx.eventLoopGroup(), config.getTrafficShapingOptions(), ap);
      resource = new CloseableResource<>() {
        @Override
        public TcpServer get() {
          return server;
        }
        @Override
        public Future<Void> shutdown(Duration timeout) {
          return server.shutdown(timeout);
        }
      };
      server.bind(config, sslOptions, vertx.acceptorEventLoopGroup(), protocol, vertx.metrics(), vertx.transport(),
        hostOrPath, context, bindAddress, localAddress);
    }

    resource.get().bindFuture.onFailure(err -> {
      channelHandler = null;
      resource.close();
    });

    ChannelHandler handler = new ChannelHandler(resource.get(), context.nettyEventLoop(),
      group, context, this.handler, exceptionHandler, resource.get().trafficShapingHandler);

    handler.init();

    this.resource = resource;
    this.channelHandler = handler;

    PromiseInternal<Channel> promise = context.promise();
    resource.get().bindFuture.onComplete(promise);
    return promise.future();
  }

  public boolean isListening() {
    return channelHandler != null;
  }

  @Override
  public boolean isMetricsEnabled() {
    CloseableResource<TcpServer> ref = resource;
    return ref != null && ref.get() != null && ref.get().metrics != null;
  }

  @Override
  public synchronized TransportMetrics<?> getMetrics() {
    CloseableResource<TcpServer> ref = resource;
    return ref == null ? null : ref.get().metrics;
  }

  private void handleShutdown(Completable<Void> completion) {
    ChannelHandler i = channelHandler;
    if (i == null) {
      completion.succeed();
      return;
    }
    i.server.channelBalancer.removeWorker(channelHandler.eventLoop, channelHandler);
    resource.close().onComplete(completion);
  }

  private void handleClose(Completable<Void> completion) {
    channelHandler = null;
    completion.succeed();
  }

  public static io.netty.util.concurrent.Future<Channel> resolveAndBind(ContextInternal context,
                                                                        SocketAddress socketAddress,
                                                                        ServerBootstrap bootstrap) {
    VertxInternal vertx = context.owner();
    io.netty.util.concurrent.Promise<Channel> promise = vertx.acceptorEventLoopGroup().next().newPromise();
    try {
      bootstrap.channelFactory(vertx.transport().serverChannelFactory(socketAddress.isDomainSocket()));
    } catch (Exception e) {
      promise.setFailure(e);
      return promise;
    }
    if (socketAddress.isDomainSocket()) {
      java.net.SocketAddress converted = vertx.transport().convert(socketAddress);
      ChannelFuture future = bootstrap.bind(converted);
      future.addListener(f -> {
        if (f.isSuccess()) {
          promise.setSuccess(future.channel());
        } else {
          promise.setFailure(f.cause());
        }
      });
    } else {
      SocketAddressImpl impl = (SocketAddressImpl) socketAddress;
      if (impl.ipAddress() != null) {
        bind(bootstrap, impl.ipAddress(), socketAddress.port(), promise);
      } else {
        NameResolver resolver = vertx.nameResolver();
        io.netty.util.concurrent.Future<InetSocketAddress> fut = resolver.resolve(context.nettyEventLoop(), socketAddress.host());
        fut.addListener((GenericFutureListener<io.netty.util.concurrent.Future<InetSocketAddress>>) future -> {
          if (future.isSuccess()) {
            bind(bootstrap, future.getNow().getAddress(), socketAddress.port(), promise);
          } else {
            promise.setFailure(future.cause());
          }
        });
      }
    }
    return promise;
  }

  private static void bind(ServerBootstrap bootstrap, InetAddress address, int port, io.netty.util.concurrent.Promise<Channel> promise) {
    InetSocketAddress t = new InetSocketAddress(address, port);
    ChannelFuture future = bootstrap.bind(t);
    future.addListener(f -> {
      if (f.isSuccess()) {
        promise.setSuccess(future.channel());
      } else {
        promise.setFailure(f.cause());
      }
    });
  }
}
