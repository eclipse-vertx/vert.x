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
package io.vertx.core.impl;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.resolver.AddressResolverGroup;
import io.vertx.core.*;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.DnsClientOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.HttpServerImpl;
import io.vertx.core.impl.btc.BlockedThreadChecker;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.impl.NetServerImpl;
import io.vertx.core.net.impl.ServerID;
import io.vertx.core.net.impl.TCPServerBase;
import io.vertx.core.spi.transport.Transport;
import io.vertx.core.shareddata.SharedData;
import io.vertx.core.spi.VerticleFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.file.FileResolver;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.core.spi.tracing.VertxTracer;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
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
  public NetServer createNetServer(NetServerOptions options) {
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
  public HttpServer createHttpServer() {
    return delegate.createHttpServer();
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
  public Timer timer(long delay, TimeUnit unit) {
    return delegate.timer(delay, unit);
  }

  @Override
  public long setTimer(long delay, Handler<Long> handler) {
    return delegate.setTimer(delay, handler);
  }

  @Override
  public TimeoutStream timerStream(long delay) {
    return delegate.timerStream(delay);
  }

  @Override
  public long setPeriodic(long initialDelay, long delay, Handler<Long> handler) {
    return delegate.setPeriodic(initialDelay, delay, handler);
  }

  @Override
  public TimeoutStream periodicStream(long initialDelay, long delay) {
    return delegate.periodicStream(initialDelay, delay);
  }

  @Override
  public boolean cancelTimer(long id) {
    return delegate.cancelTimer(id);
  }

  @Override
  public void runOnContext(Handler<Void> action) {
    delegate.runOnContext(action);
  }

  @Override
  public Future<Void> close() {
    return delegate.close();
  }

  @Override
  public void close(Handler<AsyncResult<Void>> completionHandler) {
    delegate.close(completionHandler);
  }

  @Override
  public void deployVerticle(Verticle verticle, Handler<AsyncResult<String>> completionHandler) {
    delegate.deployVerticle(verticle, completionHandler);
  }

  @Override
  public Future<String> deployVerticle(Verticle verticle, DeploymentOptions options) {
    return delegate.deployVerticle(verticle, options);
  }

  @Override
  public Future<String> deployVerticle(Class<? extends Verticle> verticleClass, DeploymentOptions options) {
    return delegate.deployVerticle(verticleClass, options);
  }

  @Override
  public Future<String> deployVerticle(Supplier<Verticle> verticleSupplier, DeploymentOptions options) {
    return delegate.deployVerticle(verticleSupplier, options);
  }

  @Override
  public void deployVerticle(Verticle verticle, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler) {
    delegate.deployVerticle(verticle, options, completionHandler);
  }

  @Override
  public void deployVerticle(Class<? extends Verticle> verticleClass, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler) {
    delegate.deployVerticle(verticleClass, options, completionHandler);
  }

  @Override
  public void deployVerticle(Supplier<Verticle> verticleSupplier, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler) {
    delegate.deployVerticle(verticleSupplier, options, completionHandler);
  }

  @Override
  public Future<String> deployVerticle(String name, DeploymentOptions options) {
    return delegate.deployVerticle(name, options);
  }

  @Override
  public void deployVerticle(String name, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler) {
    delegate.deployVerticle(name, options, completionHandler);
  }

  @Override
  public Future<Void> undeploy(String deploymentID) {
    return delegate.undeploy(deploymentID);
  }

  @Override
  public void undeploy(String deploymentID, Handler<AsyncResult<Void>> completionHandler) {
    delegate.undeploy(deploymentID, completionHandler);
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
  public <T> PromiseInternal<T> promise() {
    return delegate.promise();
  }

  @Override
  public <T> PromiseInternal<T> promise(Handler<AsyncResult<T>> handler) {
    return delegate.promise(handler);
  }

  @Override
  public long maxEventLoopExecTime() {
    return delegate.maxEventLoopExecTime();
  }

  @Override
  public TimeUnit maxEventLoopExecTimeUnit() {
    return delegate.maxEventLoopExecTimeUnit();
  }

  @Override
  public ContextInternal getOrCreateContext() {
    return delegate.getOrCreateContext();
  }

  @Override
  public EventLoopGroup getEventLoopGroup() {
    return delegate.getEventLoopGroup();
  }

  @Override
  public EventLoopGroup getAcceptorEventLoopGroup() {
    return delegate.getAcceptorEventLoopGroup();
  }

  @Override
  public WorkerPool getWorkerPool() {
    return delegate.getWorkerPool();
  }

  @Override
  public WorkerPool getInternalWorkerPool() {
    return delegate.getInternalWorkerPool();
  }

  @Override
  public Map<ServerID, HttpServerImpl> sharedHttpServers() {
    return delegate.sharedHttpServers();
  }

  @Override
  public Map<ServerID, NetServerImpl> sharedNetServers() {
    return delegate.sharedNetServers();
  }

  @Override
  public <S extends TCPServerBase> Map<ServerID, S> sharedTCPServers(Class<S> type) {
    return delegate.sharedTCPServers(type);
  }

  @Override
  public VertxMetrics metricsSPI() {
    return delegate.metricsSPI();
  }

  @Override
  public Transport transport() {
    return delegate.transport();
  }

  @Override
  public WebSocketClient createWebSocketClient(WebSocketClientOptions options) {
    return delegate.createWebSocketClient(options);
  }

  @Override
  public HttpClient createHttpPoolClient(HttpClientOptions clientOptions, PoolOptions poolOptions, CloseFuture closeFuture) {
    return delegate.createHttpPoolClient(clientOptions, poolOptions, closeFuture);
  }

  @Override
  public HttpClientBuilder httpClientBuilder() {
    return delegate.httpClientBuilder();
  }

  @Override
  public WebSocketClient createWebSocketClient(WebSocketClientOptions options, CloseFuture closeFuture) {
    return delegate.createWebSocketClient(options, closeFuture);
  }

  @Override
  public ContextInternal getContext() {
    return delegate.getContext();
  }

  @Override
  public ContextInternal createEventLoopContext(Deployment deployment, CloseFuture closeFuture, WorkerPool workerPool, ClassLoader tccl) {
    return delegate.createEventLoopContext(deployment, closeFuture, workerPool, tccl);
  }

  @Override
  public ContextInternal createEventLoopContext(EventLoop eventLoop, WorkerPool workerPool, ClassLoader tccl) {
    return delegate.createEventLoopContext(eventLoop, workerPool, tccl);
  }

  @Override
  public ContextInternal createEventLoopContext() {
    return delegate.createEventLoopContext();
  }

  @Override
  public ContextInternal createVirtualThreadContext(Deployment deployment, CloseFuture closeFuture, ClassLoader tccl) {
    return delegate.createVirtualThreadContext(deployment, closeFuture, tccl);
  }

  @Override
  public ContextInternal createVirtualThreadContext(EventLoop eventLoop, ClassLoader tccl) {
    return delegate.createVirtualThreadContext(eventLoop, tccl);
  }

  @Override
  public ContextInternal createVirtualThreadContext() {
    return delegate.createVirtualThreadContext();
  }

  @Override
  public ContextInternal createWorkerContext(EventLoop eventLoop, WorkerPool workerPool, ClassLoader tccl) {
    return delegate.createWorkerContext(eventLoop, workerPool, tccl);
  }

  @Override
  public ContextInternal createWorkerContext(Deployment deployment, CloseFuture closeFuture, WorkerPool workerPool, ClassLoader tccl) {
    return delegate.createWorkerContext(deployment, closeFuture, workerPool, tccl);
  }

  @Override
  public ContextInternal createWorkerContext() {
    return delegate.createWorkerContext();
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
  public void simulateKill() {
    delegate.simulateKill();
  }

  @Override
  public Deployment getDeployment(String deploymentID) {
    return delegate.getDeployment(deploymentID);
  }

  @Override
  public void failoverCompleteHandler(FailoverCompleteHandler failoverCompleteHandler) {
    delegate.failoverCompleteHandler(failoverCompleteHandler);
  }

  @Override
  public boolean isKilled() {
    return delegate.isKilled();
  }

  @Override
  public void failDuringFailover(boolean fail) {
    delegate.failDuringFailover(fail);
  }

  @Override
  public File resolveFile(String fileName) {
    return delegate.resolveFile(fileName);
  }

  @Override
  public ClusterManager getClusterManager() {
    return delegate.getClusterManager();
  }

  @Override
  public HAManager haManager() {
    return delegate.haManager();
  }

  @Override
  public void resolveAddress(String hostname, Handler<AsyncResult<InetAddress>> resultHandler) {
    delegate.resolveAddress(hostname, resultHandler);
  }

  @Override
  public AddressResolver addressResolver() {
    return delegate.addressResolver();
  }

  @Override
  public FileResolver fileResolver() {
    return delegate.fileResolver();
  }

  @Override
  public AddressResolverGroup<InetSocketAddress> nettyAddressResolverGroup() {
    return delegate.nettyAddressResolverGroup();
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
