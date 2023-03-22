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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.Closeable;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.impl.PartialPooledByteBufAllocator;
import io.vertx.core.impl.AddressResolver;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.SSLOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.TCPMetrics;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * Base class for TCP servers
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class TCPServerBase implements Closeable, MetricsProvider {

  private static final Logger log = LoggerFactory.getLogger(NetServerImpl.class);

  protected final Context creatingContext;
  protected final VertxInternal vertx;
  protected final NetServerOptions options;

  // Per server
  private EventLoop eventLoop;
  private BiConsumer<Channel, SslChannelProvider> childHandler;
  private Handler<Channel> worker;
  private volatile boolean listening;
  private ContextInternal listenContext;
  private TCPServerBase actualServer;

  // Main
  private SSLHelper sslHelper;
  private AtomicReference<SslChannelProvider> sslChannelProvider;
  private ServerChannelLoadBalancer channelBalancer;
  private Future<Channel> bindFuture;
  private Set<TCPServerBase> servers;
  private TCPMetrics<?> metrics;
  private volatile int actualPort;

  public TCPServerBase(VertxInternal vertx, NetServerOptions options) {
    this.vertx = vertx;
    this.options = new NetServerOptions(options);
    this.creatingContext = vertx.getContext();
    this.sslChannelProvider = new AtomicReference<>();
  }

  public SslContextProvider sslContextProvider() {
    SslChannelProvider ref = sslChannelProvider.get();
    return ref != null ? ref.sslContextProvider() : null;
  }

  public int actualPort() {
    TCPServerBase server = actualServer;
    return server != null ? server.actualPort : actualPort;
  }

  protected abstract BiConsumer<Channel, SslChannelProvider> childHandler(ContextInternal context, SocketAddress socketAddress);

  protected SSLHelper createSSLHelper() {
    return new SSLHelper(options, null);
  }

  public Future<Void> updateSSLOptions(SSLOptions options) {
    return sslHelper.buildChannelProvider(new SSLOptions(options), listenContext).andThen(ar -> {
      if (ar.succeeded()) {
        TCPServerBase.this.sslChannelProvider.set(ar.result());
      }
    }).<Void>mapEmpty();
  }

  public Future<TCPServerBase> bind(SocketAddress address) {
    ContextInternal listenContext = vertx.getOrCreateContext();
    return listen(address, listenContext).map(this);
  }

  private synchronized Future<Channel> listen(SocketAddress localAddress, ContextInternal context) {
    if (listening) {
      throw new IllegalStateException("Listen already called");
    }

    this.listenContext = context;
    this.listening = true;
    this.eventLoop = context.nettyEventLoop();

    SocketAddress bindAddress;
    Map<ServerID, TCPServerBase> sharedNetServers = vertx.sharedTCPServers((Class<TCPServerBase>) getClass());
    synchronized (sharedNetServers) {
      actualPort = localAddress.port();
      String hostOrPath = localAddress.isInetSocket() ? localAddress.host() : localAddress.path();
      TCPServerBase main;
      boolean shared;
      ServerID id;
      if (actualPort > 0 || localAddress.isDomainSocket()) {
        id = new ServerID(actualPort, hostOrPath);
        main = sharedNetServers.get(id);
        shared = true;
        bindAddress = localAddress;
      } else {
        if (actualPort < 0) {
          id = new ServerID(actualPort, hostOrPath + "/" + -actualPort);
          main = sharedNetServers.get(id);
          shared = true;
          bindAddress = SocketAddress.inetSocketAddress(0, localAddress.host());
        } else {
          id = new ServerID(actualPort, hostOrPath);
          main = null;
          shared = false;
          bindAddress = localAddress;
        }
      }
      PromiseInternal<Channel> promise = listenContext.promise();
      if (main == null) {
        // The first server binds the socket
        actualServer = this;
        bindFuture = promise;
        sslHelper = createSSLHelper();
        childHandler =  childHandler(listenContext, localAddress);
        worker = ch -> childHandler.accept(ch, sslChannelProvider.get());
        servers = new HashSet<>();
        servers.add(this);
        channelBalancer = new ServerChannelLoadBalancer(vertx.getAcceptorEventLoopGroup().next());

        // Register the server in the shared server list
        if (shared) {
          sharedNetServers.put(id, this);
        }

        listenContext.addCloseHook(this);

        // Initialize SSL before binding
        sslHelper.buildChannelProvider(options.getSslOptions(), listenContext).onComplete(ar -> {
          if (ar.succeeded()) {

            //
            sslChannelProvider.set(ar.result());

            // Socket bind
            channelBalancer.addWorker(eventLoop, worker);
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(vertx.getAcceptorEventLoopGroup(), channelBalancer.workers());
            if (options.isSsl()) {
              bootstrap.childOption(ChannelOption.ALLOCATOR, PartialPooledByteBufAllocator.INSTANCE);
            } else {
              bootstrap.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
            }

            bootstrap.childHandler(channelBalancer);
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
                  actualPort = ((InetSocketAddress)ch.localAddress()).getPort();
                }
                metrics = createMetrics(localAddress);
                promise.complete(ch);
              } else {
                promise.fail(res.cause());
              }
            });
          } else {
            promise.fail(ar.cause());
          }
        });

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
        sslChannelProvider = main.sslChannelProvider;
        childHandler =  childHandler(listenContext, localAddress);
        worker = ch -> childHandler.accept(ch, sslChannelProvider.get());
        actualServer.servers.add(this);
        actualServer.channelBalancer.addWorker(eventLoop, worker);
        listenContext.addCloseHook(this);
        main.bindFuture.onComplete(promise);
        return promise.future();
      }
    }
  }

  public boolean isListening() {
    return listening;
  }

  protected TCPMetrics<?> createMetrics(SocketAddress localAddress) {
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


  @Override
  public boolean isMetricsEnabled() {
    return metrics != null;
  }

  @Override
  public synchronized TCPMetrics<?> getMetrics() {
    return actualServer != null ? actualServer.metrics : null;
  }

  @Override
  public synchronized void close(Promise<Void> completion) {
    if (!listening) {
      completion.complete();
      return;
    }
    listening = false;
    listenContext.removeCloseHook(this);
    Map<ServerID, TCPServerBase> servers = vertx.sharedTCPServers((Class<TCPServerBase>) getClass());
    synchronized (servers) {
      ServerChannelLoadBalancer balancer = actualServer.channelBalancer;
      balancer.removeWorker(eventLoop, worker);
      if (balancer.hasHandlers()) {
        // The actual server still has handlers so we don't actually close it
        completion.complete();
      } else {
        actualServer.actualClose(completion);
      }
    }
  }

  private void actualClose(Promise<Void> done) {
    channelBalancer.close();
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

  public abstract Future<Void> close();

  public static io.netty.util.concurrent.Future<Channel> resolveAndBind(ContextInternal context,
                                                                        SocketAddress socketAddress,
                                                                        ServerBootstrap bootstrap) {
    VertxInternal vertx = context.owner();
    io.netty.util.concurrent.Promise<Channel> promise = vertx.getAcceptorEventLoopGroup().next().newPromise();
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
        AddressResolver resolver = vertx.addressResolver();
        io.netty.util.concurrent.Future<InetSocketAddress> fut = resolver.resolveHostname(context.nettyEventLoop(), socketAddress.host());
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
