/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.internal;

import io.netty.channel.EventLoopGroup;
import io.vertx.core.*;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.DnsClientOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.*;
import io.vertx.core.internal.deployment.DeploymentManager;
import io.vertx.core.internal.resolver.NameResolver;
import io.vertx.core.internal.threadchecker.BlockedThreadChecker;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.impl.NetServerInternal;
import io.vertx.core.net.impl.ServerID;
import io.vertx.core.spi.context.storage.ContextLocal;
import io.vertx.core.spi.transport.Transport;
import io.vertx.core.shareddata.SharedData;
import io.vertx.core.spi.VerticleFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.file.FileResolver;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.core.spi.tracing.VertxTracer;

import java.lang.ref.Cleaner;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A wrapper class that delegates all method calls to the {@link #delegate} instance.
 *
 * Implementing the {@link Vertx} interface is not encouraged however if that is necessary, implementations
 * should favor extending this class to ensure minimum breakage when new methods are added to the interace.
 *
 * The delegate instance can be accessed using protected final {@link #delegate} field, any method of the {@code Vertx}
 * interface can be overridden.
 */
public abstract class VertxWrapper implements VertxInternal {

  protected final VertxInternal delegate;

  protected VertxWrapper(VertxInternal delegate) {
    if (delegate == null) {
      throw new NullPointerException("Null delegate not allowed");
    }
    this.delegate = delegate;
  }

  @Override
  public <C> C createSharedResource(String resourceKey, String resourceName, CloseFuture closeFuture, Function<CloseFuture, C> supplier) {
    return delegate.createSharedResource(resourceKey, resourceName, closeFuture, supplier);
  }

  @Override
  public NetServerInternal createNetServer(NetServerOptions options) {
    return delegate.createNetServer(options);
  }

  @Override
  public NetClient createNetClient(NetClientOptions options) {
    return delegate.createNetClient(options);
  }

  @Override
  public HttpServer createHttpServer(HttpServerOptions options) {
    return delegate.createHttpServer(options);
  }

  @Override
  public HttpClientBuilder httpClientBuilder() {
    return delegate.httpClientBuilder();
  }

  @Override
  public WebSocketClient createWebSocketClient(WebSocketClientOptions options) {
    return delegate.createWebSocketClient(options);
  }

  @Override
  public DatagramSocket createDatagramSocket(DatagramSocketOptions options) {
    return delegate.createDatagramSocket(options);
  }

  @Override
  public FileSystem fileSystem() {
    return delegate.fileSystem();
  }

  @Override
  public EventBus eventBus() {
    return delegate.eventBus();
  }

  @Override
  public DnsClient createDnsClient(int port, String host) {
    return delegate.createDnsClient(port, host);
  }

  @Override
  public DnsClient createDnsClient() {
    return delegate.createDnsClient();
  }

  @Override
  public DnsClient createDnsClient(DnsClientOptions options) {
    return delegate.createDnsClient(options);
  }

  @Override
  public SharedData sharedData() {
    return delegate.sharedData();
  }

  @Override
  public long setTimer(long delay, Handler<Long> handler) {
    return delegate.setTimer(delay, handler);
  }

  @Override
  public long setPeriodic(long initialDelay, long delay, Handler<Long> handler) {
    return delegate.setPeriodic(initialDelay, delay, handler);
  }

  @Override
  public boolean cancelTimer(long id) {
    return delegate.cancelTimer(id);
  }

  @Override
  public Future<Void> close() {
    return delegate.close();
  }

  @Override
  public Future<String> deployVerticle(Supplier<? extends Deployable> supplier, DeploymentOptions options) {
    return delegate.deployVerticle(supplier, options);
  }

  @Override
  public Future<String> deployVerticle(Class<? extends Deployable> verticleClass, DeploymentOptions options) {
    return delegate.deployVerticle(verticleClass, options);
  }

  @Override
  public Future<String> deployVerticle(String name, DeploymentOptions options) {
    return delegate.deployVerticle(name, options);
  }

  @Override
  public Future<Void> undeploy(String deploymentID) {
    return delegate.undeploy(deploymentID);
  }

  @Override
  public Set<String> deploymentIDs() {
    return delegate.deploymentIDs();
  }

  @Override
  public void registerVerticleFactory(VerticleFactory factory) {
    delegate.registerVerticleFactory(factory);
  }

  @Override
  public void unregisterVerticleFactory(VerticleFactory factory) {
    delegate.unregisterVerticleFactory(factory);
  }

  @Override
  public Set<VerticleFactory> verticleFactories() {
    return delegate.verticleFactories();
  }

  @Override
  public boolean isClustered() {
    return delegate.isClustered();
  }

  @Override
  public EventLoopGroup nettyEventLoopGroup() {
    return delegate.nettyEventLoopGroup();
  }

  @Override
  public boolean isNativeTransportEnabled() {
    return delegate.isNativeTransportEnabled();
  }

  @Override
  public Throwable unavailableNativeTransportCause() {
    return delegate.unavailableNativeTransportCause();
  }

  @Override
  public Vertx exceptionHandler(Handler<Throwable> handler) {
    return delegate.exceptionHandler(handler);
  }

  @Override
  public Handler<Throwable> exceptionHandler() {
    return delegate.exceptionHandler();
  }

  @Override
  public ContextBuilder contextBuilder() {
    return delegate.contextBuilder();
  }

  @Override
  public ContextInternal getOrCreateContext() {
    return delegate.getOrCreateContext();
  }

  @Override
  public EventLoopGroup eventLoopGroup() {
    return delegate.eventLoopGroup();
  }

  @Override
  public EventLoopGroup acceptorEventLoopGroup() {
    return delegate.acceptorEventLoopGroup();
  }

  @Override
  public WorkerPool workerPool() {
    return delegate.workerPool();
  }

  @Override
  public WorkerPool internalWorkerPool() {
    return delegate.internalWorkerPool();
  }

  @Override
  public Map<ServerID, NetServerInternal> sharedTcpServers() {
    return delegate.sharedTcpServers();
  }

  @Override
  public VertxMetrics metrics() {
    return delegate.metrics();
  }

  @Override
  public Transport transport() {
    return delegate.transport();
  }

  @Override
  public Cleaner cleaner() {
    return delegate.cleaner();
  }

  @Override
  public ContextInternal getContext() {
    return delegate.getContext();
  }

  @Override
  public WorkerExecutorInternal createSharedWorkerExecutor(String name) {
    return delegate.createSharedWorkerExecutor(name);
  }

  @Override
  public WorkerExecutorInternal createSharedWorkerExecutor(String name, int poolSize) {
    return delegate.createSharedWorkerExecutor(name, poolSize);
  }

  @Override
  public WorkerExecutorInternal createSharedWorkerExecutor(String name, int poolSize, long maxExecuteTime) {
    return delegate.createSharedWorkerExecutor(name, poolSize, maxExecuteTime);
  }

  @Override
  public WorkerExecutorInternal createSharedWorkerExecutor(String name, int poolSize, long maxExecuteTime, TimeUnit maxExecuteTimeUnit) {
    return delegate.createSharedWorkerExecutor(name, poolSize, maxExecuteTime, maxExecuteTimeUnit);
  }

  @Override
  public WorkerPool createSharedWorkerPool(String name, int poolSize, long maxExecuteTime, TimeUnit maxExecuteTimeUnit) {
    return delegate.createSharedWorkerPool(name, poolSize, maxExecuteTime, maxExecuteTimeUnit);
  }

  @Override
  public WorkerPool wrapWorkerPool(ExecutorService executor) {
    return delegate.wrapWorkerPool(executor);
  }

  @Override
  public ClusterManager clusterManager() {
    return delegate.clusterManager();
  }

  @Override
  public DeploymentManager deploymentManager() {
    return delegate.deploymentManager();
  }

  @Override
  public NameResolver nameResolver() {
    return delegate.nameResolver();
  }

  @Override
  public FileResolver fileResolver() {
    return delegate.fileResolver();
  }

  @Override
  public List<ContextLocal<?>> contextLocals() {
    return delegate.contextLocals();
  }

  @Override
  public BlockedThreadChecker blockedThreadChecker() {
    return delegate.blockedThreadChecker();
  }

  @Override
  public CloseFuture closeFuture() {
    return delegate.closeFuture();
  }

  @Override
  public VertxTracer tracer() {
    return delegate.tracer();
  }

  @Override
  public void addCloseHook(Closeable hook) {
    delegate.addCloseHook(hook);
  }

  @Override
  public void removeCloseHook(Closeable hook) {
    delegate.removeCloseHook(hook);
  }

  @Override
  public boolean isVirtualThreadAvailable() {
    return delegate.isVirtualThreadAvailable();
  }

  @Override
  public boolean isMetricsEnabled() {
    return delegate.isMetricsEnabled();
  }
}
