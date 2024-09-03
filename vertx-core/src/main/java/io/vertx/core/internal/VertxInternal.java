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

package io.vertx.core.internal;


import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.resolver.AddressResolverGroup;
import io.vertx.core.*;
import io.vertx.core.dns.impl.DnsAddressResolverProvider;
import io.vertx.core.impl.*;
import io.vertx.core.impl.deployment.Deployment;
import io.vertx.core.internal.threadchecker.BlockedThreadChecker;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.impl.NetServerInternal;
import io.vertx.core.net.impl.ServerID;
import io.vertx.core.spi.transport.Transport;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.file.FileResolver;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.core.spi.tracing.VertxTracer;

import java.io.File;
import java.lang.ref.Cleaner;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * This interface provides services for vert.x core internal use only
 * It is not part of the public API and should not be used by
 * developers creating vert.x applications
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface VertxInternal extends Vertx {

  /**
   * @return the Vert.x version
   */
  static String version() {
    return VertxImpl.version();
  }

  /**
   * Create a promise and pass it to the {@code handler}, and then returns this future's promise. The {@code handler}
   * is responsible for completing the promise, if the {@code handler} throws an exception, the promise is attempted
   * to be failed with this exception.
   *
   * @param handler the handler completing the promise
   * @return the future of the created promise
   */
  default <T> Future<T> future(Handler<Promise<T>> handler) {
    return getOrCreateContext().future(handler);
  }

  /**
   * @return a promise associated with the context returned by {@link #getOrCreateContext()}.
   */
  default <T> PromiseInternal<T> promise() {
    return getOrCreateContext().promise();
  }

  /**
   * @return a promise associated with the context returned by {@link #getOrCreateContext()} or the {@code handler}
   *         if that handler is already an instance of {@code PromiseInternal}
   */
  default <T> PromiseInternal<T> promise(Promise<T> p) {
    if (p instanceof PromiseInternal) {
      PromiseInternal<T> promise = (PromiseInternal<T>) p;
      if (promise.context() != null) {
        return promise;
      }
    }
    PromiseInternal<T> promise = promise();
    promise.future().onComplete(p);
    return promise;
  }

  default void runOnContext(Handler<Void> task) {
    ContextInternal context = getOrCreateContext();
    context.runOnContext(task);
  }

  long maxEventLoopExecTime();

  TimeUnit maxEventLoopExecTimeUnit();

  NetServerInternal createNetServer(NetServerOptions options);

  default NetServerInternal createNetServer() {
    return createNetServer(new NetServerOptions());
  }

  @Override
  ContextInternal getOrCreateContext();

  EventLoopGroup getEventLoopGroup();

  EventLoopGroup getAcceptorEventLoopGroup();

  WorkerPool getWorkerPool();

  WorkerPool getInternalWorkerPool();

  Map<ServerID, NetServerInternal> sharedTcpServers();

  VertxMetrics metricsSPI();

  Transport transport();

  Cleaner cleaner();

  <C> C createSharedResource(String resourceKey, String resourceName, CloseFuture closeFuture, Function<CloseFuture, C> supplier);

  /**
   * Get the current context
   * @return the context
   */
  ContextInternal getContext();

  /**
   * @return event loop context
   */
  ContextInternal createEventLoopContext(Deployment deployment, CloseFuture closeFuture, WorkerPool workerPool, ClassLoader tccl);

  /**
   * @return event loop context
   */
  ContextInternal createEventLoopContext(EventLoop eventLoop, WorkerPool workerPool, ClassLoader tccl);

  /**
   * @return event loop context
   */
  ContextInternal createEventLoopContext();

  /**
   * @return worker context
   */
  ContextInternal createWorkerContext(Deployment deployment, CloseFuture closeFuture, EventLoop eventLoop, WorkerPool workerPool, ClassLoader tccl);

  /**
   * @return worker context
   */
  ContextInternal createWorkerContext(Deployment deployment, CloseFuture closeFuture, WorkerPool workerPool, ClassLoader tccl);

  /**
   * @return worker context
   */
  ContextInternal createWorkerContext(EventLoop eventLoop, WorkerPool workerPool, ClassLoader tccl);

  /**
   * @return worker context
   */
  ContextInternal createWorkerContext();

  /**
   * @return virtual thread context
   */
  ContextInternal createVirtualThreadContext(Deployment deployment, CloseFuture closeFuture, EventLoop eventLoop, ClassLoader tccl);

  /**
   * @return virtual thread context
   */
  ContextInternal createVirtualThreadContext(Deployment deployment, CloseFuture closeFuture, ClassLoader tccl);

  /**
   * @return virtual thread context
   */
  ContextInternal createVirtualThreadContext(EventLoop eventLoop, ClassLoader tccl);

  /**
   * @return virtual thread context
   */
  ContextInternal createVirtualThreadContext();

  @Override
  WorkerExecutorInternal createSharedWorkerExecutor(String name);

  @Override
  WorkerExecutorInternal createSharedWorkerExecutor(String name, int poolSize);

  @Override
  WorkerExecutorInternal createSharedWorkerExecutor(String name, int poolSize, long maxExecuteTime);

  @Override
  WorkerExecutorInternal createSharedWorkerExecutor(String name, int poolSize, long maxExecuteTime, TimeUnit maxExecuteTimeUnit);

  WorkerPool createSharedWorkerPool(String name, int poolSize, long maxExecuteTime, TimeUnit maxExecuteTimeUnit);

  WorkerPool wrapWorkerPool(ExecutorService executor);

  void simulateKill();

  Deployment getDeployment(String deploymentID);

  void failoverCompleteHandler(FailoverCompleteHandler failoverCompleteHandler);

  boolean isKilled();

  void failDuringFailover(boolean fail);

  File resolveFile(String fileName);

  default <T> Future<T> executeBlockingInternal(Callable<T> blockingCodeHandler) {
    ContextInternal context = getOrCreateContext();
    return context.executeBlockingInternal(blockingCodeHandler);
  }

  ClusterManager getClusterManager();

  HAManager haManager();

  /**
   * Resolve an address (e.g. {@code vertx.io} into the first found A (IPv4) or AAAA (IPv6) record.
   *
   * @param hostname the hostname to resolve
   * @return a future notified with the result
   */
  Future<InetAddress> resolveAddress(String hostname);

  /**
   * @return the default hostname resolver
   */
  HostnameResolver hostnameResolver();

  DnsAddressResolverProvider dnsAddressResolverProvider(InetSocketAddress addr);

  /**
   * @return the file resolver
   */
  FileResolver fileResolver();

  /**
   * Return the Netty EventLoopGroup used by Vert.x
   *
   * @return the EventLoopGroup
   */
  EventLoopGroup nettyEventLoopGroup();

  /**
   * @return the Netty {@code AddressResolverGroup} to use in a Netty {@code Bootstrap}
   */
  AddressResolverGroup<InetSocketAddress> nettyAddressResolverGroup();

  BlockedThreadChecker blockedThreadChecker();

  CloseFuture closeFuture();

  /**
   * @return the tracer
   */
  VertxTracer tracer();

  void addCloseHook(Closeable hook);

  void removeCloseHook(Closeable hook);

  /**
   * @return whether virtual threads are available
   */
  boolean isVirtualThreadAvailable();
}
