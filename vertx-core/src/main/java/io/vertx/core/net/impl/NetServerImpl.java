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

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.*;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.internal.CloseSequence;
import io.vertx.core.internal.resolver.NameResolver;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.impl.buffer.VertxByteBufAllocator;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.internal.net.SslChannelProvider;
import io.vertx.core.internal.net.SslHandshakeCompletionHandler;
import io.vertx.core.internal.tls.SslContextManager;
import io.vertx.core.internal.tls.SslContextProvider;
import io.vertx.core.net.*;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.TCPMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Vert.x TCP server
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class NetServerImpl implements Closeable, MetricsProvider, NetServerInternal {

  private static final Logger log = LoggerFactory.getLogger(NetServerImpl.class);
  public static final String SERVER_SSL_HANDLER_NAME = "ssl";

  private final VertxInternal vertx;
  private final NetServerOptions options;
  private final CloseSequence closeSequence;
  private Handler<NetSocket> handler;
  private Handler<Throwable> exceptionHandler;

  // Per server
  private EventLoop eventLoop;
  private NetSocketInitializer initializer;
  private ChannelGroup channelGroup;
  private Handler<Channel> worker;
  private volatile boolean listening;
  private ContextInternal listenContext;
  private NetServerImpl actualServer;
  private ShutdownEvent closeEvent;
  private ChannelGroupFuture graceFuture;

  // Main
  private SslContextManager sslContextManager;
  private volatile Future<SslContextProvider> sslContextProvider;
  private Future<SslContextProvider> updateInProgress;
  private GlobalTrafficShapingHandler trafficShapingHandler;
  private ServerChannelLoadBalancer channelBalancer;
  private Future<Channel> bindFuture;
  private Set<NetServerImpl> servers;
  private TCPMetrics<?> metrics;
  private volatile int actualPort;
  private Channel datagramChannel;

  public NetServerImpl(VertxInternal vertx, NetServerOptions options) {

    //
    // 3 steps close sequence
    // 2: a {@link CloseEvent} event is broadcast to each channel, channels should react accordingly
    // 1: grace period completed when all channels are inactive or the shutdown timeout is fired
    // 0: sockets are closed
    CloseSequence closeSequence = new CloseSequence(completion -> doClose(completion), completion1 -> doGrace(completion1), completion2 -> doShutdown(completion2));

    this.vertx = vertx;
    this.options = options;
    this.closeSequence = closeSequence;
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
    closeEvent = new ShutdownEvent(timeout, unit);
    return closeSequence.close();
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
    return listen(options.getPort(), options.getHost());
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
      if (HAProxyMessageCompletionHandler.canUseProxyProtocol(options.isUseProxyProtocol())) {
        IdleStateHandler idle;
        io.netty.util.concurrent.Promise<Channel> p = ch.eventLoop().newPromise();
        ch.pipeline().addLast(new HAProxyMessageDecoder());
        if (options.getProxyProtocolTimeout() > 0) {
          ch.pipeline().addLast("idle", idle = new IdleStateHandler(0, 0, options.getProxyProtocolTimeout(), options.getProxyProtocolTimeoutUnit()));
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
      if (options.isSsl()) {
        if (!options.isHttp3()) {
          configureChannelSslHandler(ch, sslContextProvider, null);
        }

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
      if (trafficShapingHandler != null && !options.isHttp3()) {
        ch.pipeline().addFirst("globalTrafficShaping", trafficShapingHandler);
      }
    }

    private void handleException(Throwable cause) {
      if (exceptionHandler != null) {
        context.emit(v -> exceptionHandler.handle(cause));
      }
    }

    private void connected(Channel ch, SslContextManager sslContextManager, SSLOptions sslOptions) {
      initChannel(ch.pipeline(), options.isSsl());
      TCPMetrics<?> metrics = getMetrics();
      VertxHandler<NetSocketImpl> handler = VertxHandler.create(ctx -> new NetSocketImpl(context, ctx, sslContextManager, sslOptions, metrics, options.isRegisterWriteHandler()));
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
    if (options.getLogActivity()) {
      pipeline.addLast("logging", new LoggingHandler(options.getActivityLogDataFormat()));
    }
    int idleTimeout = options.getIdleTimeout();
    int readIdleTimeout = options.getReadIdleTimeout();
    int writeIdleTimeout = options.getWriteIdleTimeout();
    if (idleTimeout > 0 || readIdleTimeout > 0 || writeIdleTimeout > 0) {
      pipeline.addLast("idle", new IdleStateHandler(readIdleTimeout, writeIdleTimeout, idleTimeout, options.getIdleTimeoutUnit()));
    }
    if (ssl || !options.isFileRegionEnabled() || !vertx.transport().supportFileRegion() || (options.getTrafficShapingOptions() != null && options.getTrafficShapingOptions().getOutboundGlobalBandwidth() > 0)) {
      // only add ChunkedWriteHandler when SSL is enabled or FileRegion isn't supported or when outbound traffic shaping is enabled
      pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());       // For large file / sendfile support
    }
  }

  protected GlobalTrafficShapingHandler createTrafficShapingHandler() {
    return createTrafficShapingHandler(vertx.eventLoopGroup(), options.getTrafficShapingOptions());
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
            sslOptions.getApplicationLayerProtocols(),
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
            if (options.isHttp3() && datagramChannel != null) {
              configureChannelSslHandler(datagramChannel, sslContextProvider.result(), channelBalancer);
            }
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
      TrafficShapingOptions prev = this.options.getTrafficShapingOptions();
      boolean updated = prev == null || !prev.equals(options);
      this.options.setTrafficShapingOptions(options);
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
    } else if (!options.equals(this.options.getTrafficShapingOptions())) {
      // Compare with existing traffic-shaping options to ensure they are updated only when they differ.
      this.options.setTrafficShapingOptions(options);
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
      DefaultChannelGroup group = new DefaultChannelGroup(listenContext.nettyEventLoop(), true);
      channelGroup = group;
      PromiseInternal<Channel> promise = listenContext.promise();
      if (main == null) {

        SslContextManager helper;
        try {
          helper = new SslContextManager(SslContextManager.resolveEngineOptions(options.getSslEngineOptions(), options.isUseAlpn()));
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
          initializer.accept(ch, scp != null ? scp.result() : null, sslContextManager, options.getSslOptions());
        };
        servers = new HashSet<>();
        servers.add(this);
        channelBalancer = new ServerChannelLoadBalancer(vertx.acceptorEventLoopGroup().next());

        //
        if (options.isHttp3() && !options.isSsl()) {
          return context.failedFuture("HTTP/3 requires SSL/TLS encryption. Please enable SSL to use HTTP/3.");
        }

        if (options.isSsl() && options.getKeyCertOptions() == null && options.getTrustOptions() == null) {
          return context.failedFuture("Key/certificate is mandatory for SSL");
        }

        // Register the server in the shared server list
        if (shared) {
          sharedNetServers.put(id, this);
        }
        listenContext.addCloseHook(this);

        // Initialize SSL before binding
        if (options.isSsl()) {
          ServerSSLOptions sslOptions = options.getSslOptions();
          configure(sslOptions);
          sslContextProvider = sslContextManager.resolveSslContextProvider(sslOptions, null,
            sslOptions.getClientAuth(), sslOptions.getApplicationLayerProtocols(), listenContext);

          sslContextProvider.onComplete(ar -> {
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
        metrics = main.metrics;
        trafficShapingHandler = main.trafficShapingHandler;
        initializer = new NetSocketInitializer(context, handler, exceptionHandler, trafficShapingHandler);
        worker = ch -> {
          group.add(ch);
          Future<SslContextProvider> scp = actualServer.sslContextProvider;
          initializer.accept(ch, scp != null ? scp.result() : null, sslContextManager, options.getSslOptions());
        };
        actualServer.servers.add(this);
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
    AbstractBootstrap bootstrap = buildServerBootstrap(localAddress);

    // Actual bind
    io.netty.util.concurrent.Future<Channel> bindFuture = resolveAndBind(context, bindAddress, bootstrap, options);
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
          actualPort = ((InetSocketAddress) ch.localAddress()).getPort();
        }
        metrics = createMetrics(localAddress);
        promise.complete(ch);
      } else {
        promise.fail(res.cause());
      }
    });
  }

  private AbstractBootstrap buildServerBootstrap(SocketAddress localAddress) {
    if (options.isHttp3()) {
      // TODO: Alter the logic of this method based on the ServerBootstrap creation in a normal scenario without HTTP/3
      Bootstrap bootstrap = new Bootstrap();
      bootstrap.group(eventLoop);
      bootstrap.handler(new ChannelInitializer<NioDatagramChannel>() {
        @Override
        protected void initChannel(NioDatagramChannel ch) throws Exception {
          datagramChannel = ch;
          applyConnectionOptions(ch);
          configureChannelSslHandler(datagramChannel, sslContextProvider.result(), NetServerImpl.this.channelBalancer);
        }
      });
      applyConnectionOptions(bootstrap);

      return bootstrap;
    }
    ServerBootstrap bootstrap = new ServerBootstrap();
    bootstrap.group(vertx.acceptorEventLoopGroup(), channelBalancer.workers());

    bootstrap.childHandler(channelBalancer);
    bootstrap.childOption(ChannelOption.ALLOCATOR, VertxByteBufAllocator.POOLED_ALLOCATOR);
    applyConnectionOptions(localAddress.isDomainSocket(), bootstrap);
    return bootstrap;
  }

  private void applyConnectionOptions(NioDatagramChannel datagramChannel) {
    DatagramSocketOptions datagramSocketOptions = new DatagramSocketOptions();

    datagramSocketOptions.setLogActivity(options.getLogActivity());
    datagramSocketOptions.setSendBufferSize(options.getSendBufferSize());
    datagramSocketOptions.setReceiveBufferSize(options.getReceiveBufferSize());
    datagramSocketOptions.setReuseAddress(options.isReuseAddress());
    datagramSocketOptions.setReusePort(options.isReusePort());
    datagramSocketOptions.setTrafficClass(options.getTrafficClass());
    datagramSocketOptions.setLogActivity(options.getLogActivity());
    datagramSocketOptions.setActivityLogDataFormat(options.getActivityLogDataFormat());

    //TODO: set the following attrs
//    datagramSocketOptions.setBrsetIpV6();
//    datagramSocketOptions.setLoopbackModeDsetIpV6();
//    datagramSocketOptions.setMulticastTimsetIpV6();
//    datagramSocketOptions.setMulticastNetworkInsetIpV6();
//    datagramSocketOptions.setIpV6();

    vertx.transport().configure(datagramChannel, datagramSocketOptions);
  }

  private void configureChannelSslHandler(Channel channel, SslContextProvider sslContextProvider, ChannelInitializer http3ChannelInitializer) {
    if (channel.pipeline().get(SERVER_SSL_HANDLER_NAME) != null) {
      channel.pipeline().remove(SERVER_SSL_HANDLER_NAME);
    }

    SslChannelProvider sslChannelProvider = new SslChannelProvider(vertx, sslContextProvider,
      options.isSni());
    channel.pipeline().addLast(SERVER_SSL_HANDLER_NAME, sslChannelProvider.createServerHandler(options.getSslOptions(),
      HttpUtils.socketAddressToHostAndPort(channel.remoteAddress()), http3ChannelInitializer));
  }

  public boolean isListening() {
    return listening;
  }

  private TCPMetrics<?> createMetrics(SocketAddress localAddress) {
    VertxMetrics metrics = vertx.metrics();
    if (metrics != null) {
      if (options instanceof HttpServerOptions) {
        return metrics.createHttpServerMetrics((HttpServerOptions) options, localAddress);
      } else {
        return metrics.createNetServerMetrics(options, localAddress);
      }
    }
    return null;
  }

  /**
   * Apply the connection option to the server.
   *
   * @param domainSocket whether it's a domain socket server
   * @param bootstrap the Netty server bootstrap
   */
  private void applyConnectionOptions(boolean domainSocket, ServerBootstrap bootstrap) {
    vertx.transport().configure(options, domainSocket, bootstrap);
  }

  private void applyConnectionOptions(Bootstrap bootstrap) {
    vertx.transport().configure(options, bootstrap);
  }


  @Override
  public boolean isMetricsEnabled() {
    return metrics != null;
  }

  @Override
  public synchronized TCPMetrics<?> getMetrics() {
    return actualServer != null ? actualServer.metrics : null;
  }

  private void doShutdown(Completable<Void> completion) {
    if (!listening) {
      completion.succeed();
      return;
    }
    if (closeEvent == null) {
      closeEvent = new ShutdownEvent(0, TimeUnit.SECONDS);
    }
    graceFuture = channelGroup.newCloseFuture();
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
      broadcastShutdownEvent(completion);
    } else {
      Promise<Void> p2 = Promise.promise();
      actualServer.actualClose(p2);
      p2.future().onComplete(ar -> {
        broadcastShutdownEvent(completion);
      });
    }
  }

  private void broadcastShutdownEvent(Completable<Void> completion) {
    for (Channel ch : channelGroup) {
      ch.pipeline().fireUserEventTriggered(closeEvent);
    }
    completion.succeed();
  }

  private void doGrace(Completable<Void> completion) {
    if (!listening) {
      completion.succeed();
      return;
    }
    if (closeEvent.timeout() > 0L) {
      long timerID = vertx.setTimer(closeEvent.timeUnit().toMillis(closeEvent.timeout()), v -> {
        completion.succeed();
      });
      graceFuture.addListener(future -> {
        if (vertx.cancelTimer(timerID)) {
          completion.succeed();
        }
      });
    } else {
      completion.succeed();
    }
  }

  private void doClose(Completable<Void> completion) {
    if (!listening) {
      completion.succeed();
      return;
    }
    listening = false;
    ChannelGroupFuture f = channelGroup.close();
//    f.addListener(future -> {
//    });
    completion.succeed();
  }

  private void actualClose(Promise<Void> done) {
    bindFuture.onComplete(ar -> {
      if (ar.succeeded()) {
        Channel channel = ar.result();
        ChannelFuture a = channel.close();
        if (metrics != null) {
          a.addListener(cg -> metrics.close());
        }
        a.addListener((PromiseInternal<Void>)done);
      } else {
        done.complete();
      }
    });
  }

  public static io.netty.util.concurrent.Future<Channel> resolveAndBind(ContextInternal context,
                                                                        SocketAddress socketAddress,
                                                                        AbstractBootstrap bootstrap,
                                                                        NetServerOptions options) {
    VertxInternal vertx = context.owner();
    io.netty.util.concurrent.Promise<Channel> promise = vertx.acceptorEventLoopGroup().next().newPromise();
    try {
      setChannelFactory(socketAddress, bootstrap, options, vertx);
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

  private static void setChannelFactory(SocketAddress socketAddress, AbstractBootstrap bootstrap,
                                        NetServerOptions options, VertxInternal vertx) {
    if (options.isHttp3()) {
      bootstrap.channelFactory(() -> vertx.transport().datagramChannel());
    } else {
      bootstrap.channelFactory(vertx.transport().serverChannelFactory(socketAddress.isDomainSocket()));
    }
  }

  private static void bind(AbstractBootstrap bootstrap, InetAddress address, int port,
                           io.netty.util.concurrent.Promise<Channel> promise) {
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

  public GlobalTrafficShapingHandler getTrafficShapingHandler() {
    return trafficShapingHandler;
  }
}
