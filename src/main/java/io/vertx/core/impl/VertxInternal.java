/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.impl;


import io.netty.channel.EventLoopGroup;
import io.netty.resolver.AddressResolverGroup;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.AsyncResult;
import io.vertx.core.Closeable;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.impl.HttpServerImpl;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.impl.NetServerImpl;
import io.vertx.core.net.impl.ServerID;
import io.vertx.core.net.impl.transport.Transport;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.metrics.VertxMetrics;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * This interface provides services for vert.x core internal use only
 * It is not part of the public API and should not be used by
 * developers creating vert.x applications
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface VertxInternal extends Vertx {

  @Override
  ContextImpl getOrCreateContext();

  EventLoopGroup getEventLoopGroup();

  EventLoopGroup getAcceptorEventLoopGroup();

  ExecutorService getWorkerPool();

  Map<ServerID, HttpServerImpl> sharedHttpServers();

  Map<ServerID, NetServerImpl> sharedNetServers();

  VertxMetrics metricsSPI();

  Transport transport();

  /**
   * Get the current context
   * @return the context
   */
  ContextImpl getContext();

  /**
   * @return event loop context
   */
  EventLoopContext createEventLoopContext(String deploymentID, WorkerPool workerPool, JsonObject config, ClassLoader tccl);

  /**
   * @return worker loop context
   */
  ContextImpl createWorkerContext(boolean multiThreaded, String deploymentID, WorkerPool pool, JsonObject config, ClassLoader tccl);

  @Override
  WorkerExecutorImpl createSharedWorkerExecutor(String name);

  @Override
  WorkerExecutorImpl createSharedWorkerExecutor(String name, int poolSize);

  @Override
  WorkerExecutorImpl createSharedWorkerExecutor(String name, int poolSize, long maxExecuteTime);

  void simulateKill();

  Deployment getDeployment(String deploymentID);

  void failoverCompleteHandler(FailoverCompleteHandler failoverCompleteHandler);

  boolean isKilled();

  void failDuringFailover(boolean fail);

  String getNodeID();

  File resolveFile(String fileName);

  <T> void executeBlockingInternal(Action<T> action, Handler<AsyncResult<T>> resultHandler);

  ClusterManager getClusterManager();

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
   * @return the Netty {@code AddressResolverGroup} to use in a Netty {@code Bootstrap}
   */
  AddressResolverGroup<InetSocketAddress> nettyAddressResolverGroup();

  @GenIgnore
  void addCloseHook(Closeable hook);

  @GenIgnore
  void removeCloseHook(Closeable hook);
}
