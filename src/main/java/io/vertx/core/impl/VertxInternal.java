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

package io.vertx.core.impl;


import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.resolver.AddressResolverGroup;
import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.core.*;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.HttpServerImpl;
import io.vertx.core.impl.btc.BlockedThreadChecker;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.net.impl.NetServerImpl;
import io.vertx.core.net.impl.ServerID;
import io.vertx.core.net.impl.TCPServerBase;
import io.vertx.core.spi.transport.Transport;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.file.FileResolver;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.core.spi.tracing.VertxTracer;

import java.io.File;
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
   * @return a promise associated with the context returned by {@link #getOrCreateContext()}.
   */
  <T> PromiseInternal<T> promise();

  /**
   * @return a promise associated with the context returned by {@link #getOrCreateContext()} or the {@code handler}
   *         if that handler is already an instance of {@code PromiseInternal}
   */
  <T> PromiseInternal<T> promise(Handler<AsyncResult<T>> handler);

  long maxEventLoopExecTime();

  TimeUnit maxEventLoopExecTimeUnit();

  @Override
  ContextInternal getOrCreateContext();

  EventLoopGroup getEventLoopGroup();

  EventLoopGroup getAcceptorEventLoopGroup();

  WorkerPool getWorkerPool();

  WorkerPool getInternalWorkerPool();

  Map<ServerID, HttpServerImpl> sharedHttpServers();

  Map<ServerID, NetServerImpl> sharedNetServers();

  <S extends TCPServerBase> Map<ServerID, S> sharedTCPServers(Class<S> type);

  VertxMetrics metricsSPI();

  Transport transport();

  /**
   * Create a WebSocket client using the specified options and close future
   *
   * @param options  the options to use
   * @param closeFuture  the close future
   * @return the client
   */
  WebSocketClient createWebSocketClient(WebSocketClientOptions options, CloseFuture closeFuture);

  /**
   * Create a HTTP/HTTPS client using the specified options and close future
   *
   * @param clientOptions the http client options to use
   * @param poolOptions the pool options to use
   * @param closeFuture  the close future
   * @return the client
   */
  HttpClient createHttpPoolClient(HttpClientOptions clientOptions, PoolOptions poolOptions, CloseFuture closeFuture);

  default <C> C createSharedClient(String clientKey, String clientName, CloseFuture closeFuture, Function<CloseFuture, C> supplier) {
    return SharedClientHolder.createSharedClient(this, clientKey, clientName, closeFuture, supplier);
  }

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

  /**
   * Like {@link #executeBlocking(Handler, Handler)} but using the internal worker thread pool.
   */
  @Deprecated
  default <T> void executeBlockingInternal(Handler<Promise<T>> blockingCodeHandler, Handler<AsyncResult<T>> resultHandler) {
    ContextInternal context = getOrCreateContext();
    context.executeBlockingInternal(blockingCodeHandler, resultHandler);
  }

  default <T> Future<T> executeBlockingInternal(Callable<T> blockingCodeHandler) {
    ContextInternal context = getOrCreateContext();
    return context.executeBlockingInternal(blockingCodeHandler);
  }

  @Deprecated
  default <T> void executeBlockingInternal(Handler<Promise<T>> blockingCodeHandler, boolean ordered, Handler<AsyncResult<T>> resultHandler) {
    ContextInternal context = getOrCreateContext();
    context.executeBlockingInternal(blockingCodeHandler, ordered, resultHandler);
  }

  default <T> Future<T> executeBlockingInternal(Callable<T> blockingCodeHandler, boolean ordered) {
    ContextInternal context = getOrCreateContext();
    return context.executeBlockingInternal(blockingCodeHandler, ordered);
  }

  ClusterManager getClusterManager();

  HAManager haManager();

  /**
   * Resolve an address (e.g. {@code vertx.io} into the first found A (IPv4) or AAAA (IPv6) record.
   *
   * @param hostname the hostname to resolve
   * @param resultHandler the result handler
   */
  void resolveAddress(String hostname, Handler<AsyncResult<InetAddress>> resultHandler);

  /**
   * @return the address resolver
   */
  AddressResolver addressResolver();

  /**
   * @return the file resolver
   */
  FileResolver fileResolver();

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
