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


import io.vertx.core.*;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.impl.btc.BlockedThreadChecker;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.impl.ServerID;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.file.FileResolver;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.core.spi.tracing.VertxTracer;

import java.io.File;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * This interface provides services for vert.x core internal use only
 * It is not part of the public API and should not be used by
 * developers creating vert.x applications
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
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

  <T> T getEventLoopGroup();

  <T> T getAcceptorEventLoopGroup();

  WorkerPool getWorkerPool();

  WorkerPool getInternalWorkerPool();

  Map<ServerID, HttpServer> sharedHttpServers();

  Map<ServerID, NetServer> sharedNetServers();

  <S> Map<ServerID, S> sharedTCPServers(Class<S> type);

  VertxMetrics metricsSPI();

  /**
   * Create a TCP/SSL client using the specified options and close future
   *
   * @param options  the options to use
   * @param closeFuture  the close future
   * @return the client
   */
  NetClient createNetClient(NetClientOptions options, CloseFuture closeFuture);

  /**
   * Create a HTTP/HTTPS client using the specified options and close future
   *
   * @param options  the options to use
   * @param closeFuture  the close future
   * @return the client
   */
  HttpClient createHttpClient(HttpClientOptions options, CloseFuture closeFuture);

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
  EventLoopContext createEventLoopContext(Deployment deployment, CloseFuture closeFuture, WorkerPool workerPool, ClassLoader tccl);

  EventLoopContext createEventLoopContext(Executor eventLoop, WorkerPool workerPool, ClassLoader tccl);

  EventLoopContext createEventLoopContext();

  /**
   * @return worker loop context
   */
  WorkerContext createWorkerContext(Deployment deployment, CloseFuture closeFuture, WorkerPool pool, ClassLoader tccl);

  WorkerContext createWorkerContext();

  @Override
  WorkerExecutorInternal createSharedWorkerExecutor(String name);

  @Override
  WorkerExecutorInternal createSharedWorkerExecutor(String name, int poolSize);

  @Override
  WorkerExecutorInternal createSharedWorkerExecutor(String name, int poolSize, long maxExecuteTime);

  @Override
  WorkerExecutorInternal createSharedWorkerExecutor(String name, int poolSize, long maxExecuteTime, TimeUnit maxExecuteTimeUnit);

  WorkerPool createSharedWorkerPool(String name, int poolSize, long maxExecuteTime, TimeUnit maxExecuteTimeUnit);

  void simulateKill();

  Deployment getDeployment(String deploymentID);

  void failoverCompleteHandler(FailoverCompleteHandler failoverCompleteHandler);

  boolean isKilled();

  void failDuringFailover(boolean fail);

  File resolveFile(String fileName);

  /**
   * Like {@link #executeBlocking(Handler, Handler)} but using the internal worker thread pool.
   */
  default <T> void executeBlockingInternal(Handler<Promise<T>> blockingCodeHandler, Handler<AsyncResult<T>> resultHandler) {
    ContextInternal context = getOrCreateContext();
    context.executeBlockingInternal(blockingCodeHandler, resultHandler);
  }

  default <T> void executeBlockingInternal(Handler<Promise<T>> blockingCodeHandler, boolean ordered, Handler<AsyncResult<T>> resultHandler) {
    ContextInternal context = getOrCreateContext();
    context.executeBlockingInternal(blockingCodeHandler, ordered, resultHandler);
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

  BlockedThreadChecker blockedThreadChecker();

  CloseFuture closeFuture();

  /**
   * @return the tracer
   */
  VertxTracer tracer();

  void addCloseHook(Closeable hook);

  void removeCloseHook(Closeable hook);

}
