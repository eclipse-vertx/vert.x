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
import io.netty.channel.ChannelFutureListener;
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
import io.vertx.core.http.ClientAuth;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.impl.buffer.VertxByteBufAllocator;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.internal.net.SslChannelProvider;
import io.vertx.core.internal.net.SslHandshakeCompletionHandler;
import io.vertx.core.internal.resolver.NameResolver;
import io.vertx.core.internal.tls.SslContextManager;
import io.vertx.core.internal.tls.SslContextProvider;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.*;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.TransportMetrics;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Vert.x TCP server
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class NetServerImpl implements Closeable, MetricsProvider, NetServerInternal {

  private static final Logger log = LoggerFactory.getLogger(NetServerImpl.class);

  private final VertxInternal vertx;
  private final TcpServerConfig config;
  private final ServerSSLOptions sslOptions;
  private final boolean fileRegionEnabled;
  private final boolean registerWriteHandler;
  private final TransportMetrics<?> metrics;
  private Handler<NetSocket> handler;
  private Handler<Throwable> exceptionHandler;

  // Per server
  private EventLoop eventLoop;
  private NetSocketInitializer initializer;
  private ConnectionGroup channelGroup;
  private Handler<Channel> worker;
  private volatile boolean listening;
  private ContextInternal listenContext;
  private NetServerImpl actualServer;

  // Main
  private SslContextManager sslContextManager;
  private volatile Future<SslContextProvider> sslContextProvider;
  private Future<SslContextProvider> updateInProgress;
  private GlobalTrafficShapingHandler trafficShapingHandler;
  private ServerChannelLoadBalancer channelBalancer;
  private Future<Channel> bindFuture;
  private volatile int actualPort;
  private volatile SocketAddress actualLocalAddress;

  public NetServerImpl(VertxInternal vertx,
                       TcpServerConfig config,
                       ServerSSLOptions sslOptions,
                       boolean fileRegionEnabled,
                       boolean registerWriteHandler,
                       TransportMetrics<?> metrics) {

    if (sslOptions == null) {
      sslOptions = new ServerSSLOptions();
    }

    this.vertx = vertx;
    this.config = config;
    this.fileRegionEnabled = fileRegionEnabled;
    this.registerWriteHandler = registerWriteHandler;
    this.sslOptions = sslOptions;
    this.metrics = metrics;
  }

  public SslContextProvider sslContextProvider() {
    return sslContextProvider.result();
  }

  @Override
  public synchronized Handler<NetSocket> connectHandler() {
    return handler;
  }

  @Override
  public synchronized NetServerInternal connectHandler(Handler<NetSocket> handler) {
    if (isListening()) {
      throw new IllegalStateException("Cannot set connectHandler when server is listening");
    }
    this.handler = handler;
    return this;
  }

  @Override
  public synchronized NetServerInternal exceptionHandler(Handler<Throwable> handler) {
    if (isListening()) {
      throw new IllegalStateException("Cannot set exceptionHandler when server is listening");
    }
    this.exceptionHandler = handler;
    return this;
  }

  public int actualPort() {
    NetServerImpl server = actualServer;
    return server != null ? server.actualPort : actualPort;
  }

  @Override
  public Future<Void> shutdown(long timeout, TimeUnit unit) {
    ConnectionGroup group = channelGroup;
    if (group == null) {
      return vertx.getOrCreateContext().succeededFuture();
    }
    return group.shutdown(timeout, unit);
  }

  @Override
  public Future<NetServer> listen(SocketAddress localAddress) {
    return listen(vertx.getOrCreateContext(), localAddress);
  }

  @Override
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

  @Override
  public synchronized void close(Completable<Void> completion) {
    shutdown(0L, TimeUnit.SECONDS).onComplete(completion);
  }

  public boolean isClosed() {
    return !isListening();
  }

  private class NetSocketInitializer {

    private final ContextInternal context;
    private final Handler<NetSocket> connectionHandler;
    private final Handler<Throwable> exceptionHandler;
    private final GlobalTrafficShapingHandler trafficShapingHandler;

    NetSocketInitializer(ContextInternal context, Handler<NetSocket> connectionHandler, Handler<Throwable> exceptionHandler, GlobalTrafficShapingHandler trafficShapingHandler) {
      this.context = context;
      this.connectionHandler = connectionHandler;
      this.exceptionHandler = exceptionHandler;
      this.trafficShapingHandler = trafficShapingHandler;
    }

    protected synchronized boolean accept() {
      return true;
    }

    public void accept(Channel ch, SslContextProvider sslChannelProvider, SslContextManager sslContextManager, ServerSSLOptions sslOptions) {
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
            configurePipeline(future.getNow(), sslChannelProvider, sslContextManager, sslOptions);
          } else {
            //No need to close the channel.HAProxyMessageDecoder already did
            handleException(future.cause());
          }
        });
      } else {
        configurePipeline(ch, sslChannelProvider, sslContextManager, sslOptions);
      }
    }

    private void configurePipeline(Channel ch, SslContextProvider sslContextProvider, SslContextManager sslContextManager, ServerSSLOptions sslOptions) {
      if (config.isSsl()) {
        List<String> applicationProtocols;
        if (sslOptions.isUseAlpn()) {
          applicationProtocols = sslOptions.getApplicationLayerProtocols();
        } else {
          applicationProtocols = null;
        }
        SslChannelProvider sslChannelProvider = new SslChannelProvider(vertx, sslContextProvider, sslOptions.isSni());
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

    private void connected(Channel ch, SslContextManager sslContextManager, SSLOptions sslOptions) {
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
    if (config.getNetworkLogging() != null) {
      pipeline.addLast("logging", new LoggingHandler(config.getNetworkLogging().getDataFormat()));
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

  protected GlobalTrafficShapingHandler createTrafficShapingHandler() {
    return createTrafficShapingHandler(vertx.eventLoopGroup(), config.getTrafficShapingOptions());
  }

  private GlobalTrafficShapingHandler createTrafficShapingHandler(EventLoopGroup eventLoopGroup, TrafficShapingOptions options) {
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

  protected void configure(SSLOptions options) {
  }

  public int sniEntrySize() {
    return sslContextManager.sniEntrySize();
  }

  public Future<Boolean> updateSSLOptions(ServerSSLOptions options, boolean force) {
    NetServerImpl server = actualServer;
    if (server != null && server != this) {
      return server.updateSSLOptions(options, force);
    } else {
      ContextInternal ctx = vertx.getOrCreateContext();
      Future<SslContextProvider> fut;
      SslContextProvider current;
      synchronized (this) {
        current = sslContextProvider.result();
        if (updateInProgress == null) {
          ServerSSLOptions sslOptions = options.copy();
          configure(sslOptions);
          ClientAuth clientAuth = sslOptions.getClientAuth();
          if (clientAuth == null) {
            clientAuth = ClientAuth.NONE;
          }
          updateInProgress = sslContextManager.resolveSslContextProvider(
            sslOptions,
            null,
            clientAuth,
            force,
            ctx);
          fut = updateInProgress;
        } else {
          return updateInProgress.mapEmpty().transform(ar -> updateSSLOptions(options, force));
        }
      }
      fut.onComplete(ar -> {
        synchronized (this) {
          updateInProgress = null;
          if (ar.succeeded()) {
            sslContextProvider = fut;
          }
        }
      });
      return fut.map(res -> res != current);
    }
  }

  public Future<Boolean> updateTrafficShapingOptions(TrafficShapingOptions options) {
    if (options == null) {
      throw new IllegalArgumentException("Invalid null value passed for traffic shaping options update");
    }
    NetServerImpl server = actualServer;
    ContextInternal ctx = vertx.getOrCreateContext();
    if (server == null) {
      // Server not yet started
      TrafficShapingOptions prev = this.config.getTrafficShapingOptions();
      boolean updated = prev == null || !prev.equals(options);
      this.config.setTrafficShapingOptions(options);
      return ctx.succeededFuture(updated);
    }
    // Update the traffic shaping options only for the actual/main server
    if (server != this) {
      return server.updateTrafficShapingOptions(options);
    } else {
      Promise<Boolean> promise = ctx.promise();
      ctx.emit(v -> updateTrafficShapingOptions(options, promise));
      return promise.future();
    }
  }

  public void updateTrafficShapingOptions(TrafficShapingOptions options, Promise<Boolean> promise) {
    if (trafficShapingHandler == null) {
      promise.fail(new IllegalStateException("Unable to update traffic shaping options because the server was not configured " +
        "to use traffic shaping during startup"));
    } else if (!options.equals(this.config.getTrafficShapingOptions())) {
      // Compare with existing traffic-shaping options to ensure they are updated only when they differ.
      this.config.setTrafficShapingOptions(options);
      long checkIntervalForStatsInMillis = options.getCheckIntervalForStatsTimeUnit().toMillis(options.getCheckIntervalForStats());
      trafficShapingHandler.configure(options.getOutboundGlobalBandwidth(), options.getInboundGlobalBandwidth(), checkIntervalForStatsInMillis);
      if (options.getPeakOutboundGlobalBandwidth() != 0) {
        trafficShapingHandler.setMaxGlobalWriteSize(options.getPeakOutboundGlobalBandwidth());
      }
      if (options.getMaxDelayToWait() != 0) {
        long maxDelayToWaitInMillis = options.getMaxDelayToWaitTimeUnit().toMillis(options.getMaxDelayToWait());
        trafficShapingHandler.setMaxWriteDelay(maxDelayToWaitInMillis);
      }
      promise.complete(true);
    } else {
      log.info("Not updating traffic shaping options as they have not changed");
      promise.complete(false);
    }
  }

  private synchronized Future<Channel> bind(ContextInternal context, SocketAddress localAddress) {
    if (listening) {
      throw new IllegalStateException("Listen already called");
    }

    this.listenContext = context;
    this.listening = true;
    this.eventLoop = context.nettyEventLoop();

    SocketAddress bindAddress;
    Map<ServerID, NetServerInternal> sharedNetServers = vertx.sharedTcpServers();
    synchronized (sharedNetServers) {
      actualPort = localAddress.port();
      String hostOrPath = localAddress.isInetSocket() ? localAddress.host() : localAddress.path();
      NetServerImpl main;
      boolean shared;
      ServerID id;
      if (actualPort > 0 || localAddress.isDomainSocket()) {
        id = new ServerID(actualPort, hostOrPath);
        main = (NetServerImpl) sharedNetServers.get(id);
        shared = true;
        bindAddress = localAddress;
      } else {
        if (actualPort < 0) {
          id = new ServerID(actualPort, hostOrPath + "/" + -actualPort);
          main = (NetServerImpl) sharedNetServers.get(id);
          shared = true;
          bindAddress = SocketAddress.inetSocketAddress(0, localAddress.host());
        } else {
          id = new ServerID(actualPort, hostOrPath);
          main = null;
          shared = false;
          bindAddress = localAddress;
        }
      }
      ConnectionGroup group = new ConnectionGroup(listenContext.nettyEventLoop()) {
        @Override
        protected void handleClose(Completable<Void> completion) {
          NetServerImpl.this.handleClose(completion);
        }
        @Override
        protected void handleShutdown(Duration timeout, Completable<Void> completion) {
          NetServerImpl.this.handleShutdown(completion);
        }
      };
      channelGroup = group;
      PromiseInternal<Channel> promise = listenContext.promise();
      if (main == null) {

        SslContextManager helper;
        try {
          helper = new SslContextManager(SslContextManager.resolveEngineOptions(config.getSslEngineOptions(), sslOptions.isUseAlpn()));
        } catch (Exception e) {
          return context.failedFuture(e);
        }

        // The first server binds the socket
        actualServer = this;
        bindFuture = promise;
        sslContextManager = helper;
        trafficShapingHandler = createTrafficShapingHandler();
        initializer = new NetSocketInitializer(context, handler, exceptionHandler, trafficShapingHandler);
        worker = ch -> {
          // Should close if the channel group is closed actually or check that
          channelGroup.add(ch);
          Future<SslContextProvider> scp = sslContextProvider;
          initializer.accept(ch, scp != null ? scp.result() : null, sslContextManager, sslOptions);
        };
        channelBalancer = new ServerChannelLoadBalancer(vertx.acceptorEventLoopGroup().next());

        //
        if (config.isSsl() && sslOptions.getKeyCertOptions() == null && sslOptions.getTrustOptions() == null) {
          return context.failedFuture("Key/certificate is mandatory for SSL");
        }

        // Register the server in the shared server list
        if (shared) {
          sharedNetServers.put(id, this);
        }
        listenContext.addCloseHook(this);

        // Initialize SSL before binding
        if (config.isSsl()) {
          ServerSSLOptions sslOptions = this.sslOptions;
          configure(sslOptions);
          sslContextProvider = sslContextManager.resolveSslContextProvider(sslOptions, listenContext).onComplete(ar -> {
            if (ar.succeeded()) {
              bind(hostOrPath, context, bindAddress, localAddress, shared, promise, sharedNetServers, id);
            } else {
              promise.fail(ar.cause());
            }
          });
        } else {
          bind(hostOrPath, context, bindAddress, localAddress, shared, promise, sharedNetServers, id);
        }

        bindFuture.onFailure(err -> {
          if (shared) {
            synchronized (sharedNetServers) {
              sharedNetServers.remove(id);
            }
          }
          listening = false;
        });

        return bindFuture;
      } else {
        // Server already exists with that host/port - we will use that
        actualServer = main;
        trafficShapingHandler = main.trafficShapingHandler;
        initializer = new NetSocketInitializer(context, handler, exceptionHandler, trafficShapingHandler);
        worker = ch -> {
          group.add(ch);
          Future<SslContextProvider> scp = actualServer.sslContextProvider;
          initializer.accept(ch, scp != null ? scp.result() : null, sslContextManager, sslOptions);
        };
        actualServer.channelBalancer.addWorker(eventLoop, worker);
        listenContext.addCloseHook(this);
        main.bindFuture.onComplete(promise);
        return promise.future();
      }
    }
  }

  private void bind(
    String hostOrPath,
    ContextInternal context,
    SocketAddress bindAddress,
    SocketAddress localAddress,
    boolean shared,
    Promise<Channel> promise,
    Map<ServerID, NetServerInternal> sharedNetServers,
    ServerID id) {
    // Socket bind
    channelBalancer.addWorker(eventLoop, worker);
    ServerBootstrap bootstrap = new ServerBootstrap();
    bootstrap.group(vertx.acceptorEventLoopGroup(), channelBalancer.workers());
    bootstrap.childHandler(channelBalancer);
    bootstrap.childOption(ChannelOption.ALLOCATOR, VertxByteBufAllocator.POOLED_ALLOCATOR);
    applyConnectionOptions(localAddress.isDomainSocket(), bootstrap);

    // Actual bind
    io.netty.util.concurrent.Future<Channel> bindFuture = resolveAndBind(context, bindAddress, bootstrap);
    bindFuture.addListener((GenericFutureListener<io.netty.util.concurrent.Future<Channel>>) res -> {
      if (res.isSuccess()) {
        Channel ch = res.getNow();
        log.trace("Net server listening on " + hostOrPath + ":" + ch.localAddress());
        if (shared) {
          ch.closeFuture().addListener((ChannelFutureListener) channelFuture -> {
            synchronized (sharedNetServers) {
              sharedNetServers.remove(id);
            }
          });
        }
        // Update port to actual port when it is not a domain socket as wildcard port 0 might have been used
        if (bindAddress.isInetSocket()) {
          int port = ((InetSocketAddress) ch.localAddress()).getPort();
          actualPort = port;
          actualLocalAddress = SocketAddress.inetSocketAddress(port, localAddress.host());
        } else {
          actualLocalAddress = localAddress;
        }
        if (metrics != null) {
          metrics.bound(true, actualLocalAddress);
        }
        promise.complete(ch);
      } else {
        promise.fail(res.cause());
      }
    });
  }

  public boolean isListening() {
    return listening;
  }

  /**
   * Apply the connection option to the server.
   *
   * @param domainSocket whether it's a domain socket server
   * @param bootstrap the Netty server bootstrap
   */
  private void applyConnectionOptions(boolean domainSocket, ServerBootstrap bootstrap) {

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

    vertx.transport().configure(config.getTransportConfig(), domainSocket, bootstrap);
  }


  @Override
  public boolean isMetricsEnabled() {
    return metrics != null;
  }

  @Override
  public synchronized TransportMetrics<?> getMetrics() {
    return actualServer != null ? actualServer.metrics : null;
  }

  private void handleShutdown(Completable<Void> completion) {
    if (!listening) {
      completion.succeed();
      return;
    }
    listenContext.removeCloseHook(this);
    Map<ServerID, NetServerInternal> servers = vertx.sharedTcpServers();
    boolean hasHandlers;
    synchronized (servers) {
      ServerChannelLoadBalancer balancer = actualServer.channelBalancer;
      balancer.removeWorker(eventLoop, worker);
      hasHandlers = balancer.hasHandlers();
    }
    // THIS CAN BE RACY
    if (hasHandlers) {
      // The actual server still has handlers so we don't actually close it
      completion.succeed();
    } else {
      Promise<Void> p2 = Promise.promise();
      actualServer.actualClose(p2);
      p2.future().onComplete(ar -> {
        completion.succeed();
      });
    }
  }

  private void handleClose(Completable<Void> completion) {
    listening = false;
    completion.succeed();
  }

  private void actualClose(Promise<Void> done) {
    bindFuture.onComplete(ar -> {
      if (ar.succeeded()) {
        Channel channel = ar.result();
        ChannelFuture a = channel.close();
        SocketAddress addr = actualLocalAddress;
        actualLocalAddress = null;
        if (metrics != null) {
          a.addListener(cg -> {
            if (addr != null) {
              metrics.unbound(true, addr);
            }
            metrics.close();
          });
        }
        a.addListener((PromiseInternal<Void>)done);
      } else {
        done.complete();
      }
    });
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
