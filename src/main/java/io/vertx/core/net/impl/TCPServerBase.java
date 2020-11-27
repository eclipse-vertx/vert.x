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
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.AsyncResult;
import io.vertx.core.Closeable;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.MetricsProvider;
import io.vertx.core.spi.metrics.TCPMetrics;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
  protected final SSLHelper sslHelper;

  // Per server
  private EventLoop eventLoop;
  private Handler<Channel> worker;
  private volatile boolean listening;
  private ContextInternal listenContext;
  private ServerID id;
  private TCPServerBase actualServer;

  // Main
  private ServerChannelLoadBalancer channelBalancer;
  private io.netty.util.concurrent.Future<Channel> bindFuture;
  private Set<TCPServerBase> servers;
  private TCPMetrics<?> metrics;
  private volatile int actualPort;

  public TCPServerBase(VertxInternal vertx, NetServerOptions options) {
    this.vertx = vertx;
    this.options = new NetServerOptions(options);
    this.sslHelper = new SSLHelper(options, options.getKeyCertOptions(), options.getTrustOptions());
    this.creatingContext = vertx.getContext();
  }

  public int actualPort() {
    TCPServerBase server = actualServer;
    return server != null ? server.actualPort : actualPort;
  }

  public synchronized io.netty.util.concurrent.Future<Channel> listen(SocketAddress localAddress, ContextInternal context, Handler<Channel> worker) {
    if (listening) {
      throw new IllegalStateException("Listen already called");
    }

    this.listenContext = context;
    this.listening = true;
    this.eventLoop = context.nettyEventLoop();
    this.worker = worker;

    Map<ServerID, TCPServerBase> sharedNetServers = vertx.sharedTCPServers((Class<TCPServerBase>) getClass());
    synchronized (sharedNetServers) {
      actualPort = localAddress.port();
      String hostOrPath = localAddress.isInetSocket() ? localAddress.host() : localAddress.path();
      TCPServerBase main;
      boolean shared;
      if (actualPort != 0) {
        id = new ServerID(actualPort, hostOrPath);
        main = sharedNetServers.get(id);
        shared = true;
      } else {
        if (creatingContext != null && creatingContext.deploymentID() != null) {
          id = new ServerID(actualPort, hostOrPath + "/" + creatingContext.deploymentID());
          main = sharedNetServers.get(id);
          shared = true;
        } else {
          id = new ServerID(actualPort, hostOrPath);
          main = null;
          shared = false;
        }
      }
      if (main == null) {
        servers = new HashSet<>();
        servers.add(this);
        channelBalancer = new ServerChannelLoadBalancer(vertx.getAcceptorEventLoopGroup().next());
        channelBalancer.addWorker(eventLoop, worker);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(vertx.getAcceptorEventLoopGroup(), channelBalancer.workers());

        bootstrap.childHandler(channelBalancer);
        applyConnectionOptions(localAddress.isDomainSocket(), bootstrap);

        try {
          sslHelper.validate(vertx);
          bindFuture = AsyncResolveConnectHelper.doBind(vertx, localAddress, bootstrap);
          bindFuture.addListener((GenericFutureListener<io.netty.util.concurrent.Future<Channel>>) res -> {
            if (res.isSuccess()) {
              Channel ch = res.getNow();
              log.trace("Net server listening on " + hostOrPath + ":" + ch.localAddress());
              // Update port to actual port when it is not a domain socket as wildcard port 0 might have been used
              if (actualPort != -1) {
                actualPort = ((InetSocketAddress)ch.localAddress()).getPort();
              }
              id = new ServerID(TCPServerBase.this.actualPort, id.host);
              listenContext.addCloseHook(this);
              metrics = createMetrics(localAddress);
            } else {
              if (shared) {
                synchronized (sharedNetServers) {
                  sharedNetServers.remove(id);
                }
              }
              listening  = false;
            }
          });
        } catch (Throwable t) {
          listening = false;
          return vertx.getAcceptorEventLoopGroup().next().newFailedFuture(t);
        }
        if (shared) {
          sharedNetServers.put(id, this);
        }
        actualServer = this;
      } else {
        // Server already exists with that host/port - we will use that
        actualServer = main;
        actualServer.servers.add(this);
        actualServer.channelBalancer.addWorker(eventLoop, worker);
        metrics = main.metrics;
        listenContext.addCloseHook(this);
      }
    }

    return actualServer.bindFuture;
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
        // No worker left so close the actual server
        // The done handler needs to be executed on the context that calls close, NOT the context
        // of the actual server
        servers.remove(id);
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

  /**
   * Internal method that closes all servers when Vert.x is closing
   */
  public void closeAll(Handler<AsyncResult<Void>> handler) {
    List<Future> futures = new ArrayList<>(actualServer.servers)
      .stream()
      .map(TCPServerBase::close)
      .collect(Collectors.toList());
    CompositeFuture fut = CompositeFuture.all(futures);
    fut.onComplete(ar -> handler.handle(ar.mapEmpty()));
  }

  public abstract Future<Void> close();

  @Override
  protected void finalize() throws Throwable {
    // Make sure this gets cleaned up if there are no more references to it
    // so as not to leave connections and resources dangling until the system is shutdown
    // which could make the JVM run out of file handles.
    close();
    super.finalize();
  }
}
