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
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.TCPMetrics;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

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
  private Handler<Channel> worker;
  private volatile CountDownLatch initialization = new CountDownLatch(1);
  private volatile boolean listening;
  private ContextInternal listenContext;
  private TCPServerBase actualServer;

  // Main
  private SSLHelper sslHelper;
  private ServerChannelLoadBalancer channelBalancer;
  private io.netty.util.concurrent.Future<Channel> bindFuture;
  private Set<TCPServerBase> servers;
  private TCPMetrics<?> metrics;
  private volatile int actualPort;

  public TCPServerBase(VertxInternal vertx, NetServerOptions options) {
    this.vertx = vertx;
    this.options = new NetServerOptions(options);
    this.creatingContext = vertx.getContext();
  }

  public int actualPort() {
    TCPServerBase server = actualServer;
    return server != null ? server.actualPort : actualPort;
  }

  protected abstract Handler<Channel> childHandler(ContextInternal context, SocketAddress socketAddress, SSLHelper sslHelper);

  protected SSLHelper createSSLHelper() {
    return new SSLHelper(options, options.getKeyCertOptions(), options.getTrustOptions());
  }

  public synchronized SSLHelper sslHelper() {
    return sslHelper;
  }

  public Future<TCPServerBase> bind(SocketAddress address) {
    ContextInternal listenContext = vertx.getOrCreateContext();
    Promise<TCPServerBase> promise = listenContext.promise();

    listen(address, listenContext)
      .onSuccess(bindFuture -> bindFuture.addListener(res -> {
          if (res.isSuccess()) {
            promise.complete(this);
          } else {
            promise.fail(res.cause());
          }
        }))
      .onFailure(promise::fail);

    return promise.future();
  }

  private synchronized Future<io.netty.util.concurrent.Future<Channel>> listen(SocketAddress localAddress, ContextInternal context) {
    if (listening) {
      throw new IllegalStateException("Listen already called");
    }

    this.listenContext = context;
    this.listening = true;
    this.eventLoop = context.nettyEventLoop();

    SocketAddress bindAddress;
    actualPort = localAddress.port();
    String hostOrPath = localAddress.isInetSocket() ? localAddress.host() : localAddress.path();
    boolean shared;
    ServerID id;

    if (actualPort > 0 || localAddress.isDomainSocket()) {
      id = new ServerID(actualPort, hostOrPath);
      shared = true;
      bindAddress = localAddress;
    } else {
      if (actualPort < 0) {
        id = new ServerID(actualPort, hostOrPath + "/" + -actualPort);
        shared = true;
        bindAddress = SocketAddress.inetSocketAddress(0, localAddress.host());
      } else {
        id = new ServerID(actualPort, hostOrPath);
        shared = false;
        bindAddress = localAddress;
      }
    }

    Map<ServerID, TCPServerBase> sharedNetServers = vertx.sharedTCPServers((Class<TCPServerBase>) getClass());
    Promise<io.netty.util.concurrent.Future<Channel>> promise = listenContext.promise();

    if (shared) {
      synchronized (sharedNetServers) {
        TCPServerBase main = sharedNetServers.get(id);

        if (main != null) {
          awaitInitialization(context, main)
            .onComplete(initResult -> {
              if (initResult.succeeded()) {
                if (main.isListening()) {
                  // Server already exists with that host/port - we will use that
                  actualServer = main;
                  metrics = main.metrics;
                  sslHelper = main.sslHelper;
                  worker =  childHandler(listenContext, localAddress, sslHelper);
                  actualServer.servers.add(this);
                  actualServer.channelBalancer.addWorker(eventLoop, worker);
                  listenContext.addCloseHook(this);
                  promise.complete(actualServer.bindFuture);
                } else {
                  listenNew(localAddress, context, bindAddress, sharedNetServers, shared, hostOrPath, id, promise);
                }
              } else {
                promise.fail(initResult.cause());
              }
            });
        } else {
          sharedNetServers.put(id, this);
          listenNew(localAddress, context, bindAddress, sharedNetServers, shared, hostOrPath, id, promise);
        }
      }
    } else {
      listenNew(localAddress, context, bindAddress, sharedNetServers, shared, hostOrPath, id, promise);
    }

    return promise.future();
  }

  private Future<Void> awaitInitialization(ContextInternal context, TCPServerBase main) {
    return context.executeBlocking(initWaitFut -> {
      try {
        main.initialization.await();
        initWaitFut.complete();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        initWaitFut.fail(e);
      }
    });
  }

  private synchronized void listenNew(SocketAddress localAddress,
                                      ContextInternal context,
                                      SocketAddress bindAddress,
                                      Map<ServerID, TCPServerBase> sharedNetServers,
                                      boolean shared,
                                      String hostOrPath,
                                      ServerID id,
                                      Promise<io.netty.util.concurrent.Future<Channel>> promise) {
    try {
      sslHelper = createSSLHelper();
      sslHelper.validate(vertx, context)
        .onComplete(validateResult -> {
          if (validateResult.succeeded()) {
            try {
              promise.complete(listenValidated(localAddress, bindAddress, sharedNetServers, shared, hostOrPath, id));
              initialization.countDown();
            } catch (Exception e) {
              promise.fail(e);
              synchronized (sharedNetServers) {
                sharedNetServers.remove(id);
              }
              cancelInitialization();
            }
          } else {
            synchronized (sharedNetServers) {
              sharedNetServers.remove(id);
            }
            cancelInitialization();
            promise.fail(validateResult.cause());
          }
        });
    } catch (Throwable t) {
      cancelInitialization();
      promise.fail(t);
    }
  }

  private synchronized io.netty.util.concurrent.Future<Channel> listenValidated(SocketAddress localAddress,
                                                                                SocketAddress bindAddress,
                                                                                Map<ServerID, TCPServerBase> sharedNetServers,
                                                                                boolean shared,
                                                                                String hostOrPath,
                                                                                ServerID id) {
    try {
      worker =  childHandler(listenContext, localAddress, sslHelper);
      servers = new HashSet<>();
      servers.add(this);
      channelBalancer = new ServerChannelLoadBalancer(vertx.getAcceptorEventLoopGroup().next());
      channelBalancer.addWorker(eventLoop, worker);

      ServerBootstrap bootstrap = new ServerBootstrap();
      bootstrap.group(vertx.getAcceptorEventLoopGroup(), channelBalancer.workers());
      if (sslHelper.isSSL()) {
        bootstrap.childOption(ChannelOption.ALLOCATOR, PartialPooledByteBufAllocator.INSTANCE);
      } else {
        bootstrap.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
      }

      bootstrap.childHandler(channelBalancer);
      applyConnectionOptions(localAddress.isDomainSocket(), bootstrap);

      bindFuture = AsyncResolveConnectHelper.doBind(vertx, bindAddress, bootstrap);
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
          listenContext.addCloseHook(this);
          metrics = createMetrics(localAddress);
        } else {
          if (shared) {
            synchronized (sharedNetServers) {
              sharedNetServers.remove(id);
            }
          }
          listening = false;
        }
      });
    } catch (Throwable t) {
      return vertx.getAcceptorEventLoopGroup().next().newFailedFuture(t);
    }

    actualServer = this;
    return actualServer.bindFuture;
  }

  private void cancelInitialization() {
    listening = false;
    initialization.countDown();
    initialization = new CountDownLatch(1);
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
    bindFuture.addListener((GenericFutureListener<io.netty.util.concurrent.Future<Channel>>) fut -> {
      if (fut.isSuccess()) {
        Channel channel = fut.getNow();
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

}
