package io.vertx.core;

import io.netty.channel.EventLoop;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.impl.CloseFuture;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.Deployment;
import io.vertx.core.impl.TaskQueue;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.WorkerPool;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

class FakeContext implements ContextInternal {

  private final VertxImpl impl;
  private final ClassLoader tccl;

  public FakeContext(VertxImpl impl, ClassLoader classLoader) {
    this.impl = impl;
    this.tccl = classLoader;
  }

  @Override
  public Executor executor() {
    return command -> {

    };
  }

  @Override
  public boolean inThread() {
    return false;
  }

  @Override
  public <T> Future<@Nullable T> executeBlocking(Callable<T> blockingCodeHandler, boolean ordered) {
    return null;
  }

  @Override
  public String deploymentID() {
    return null;
  }

  @Override
  public @Nullable JsonObject config() {
    return null;
  }

  @Override
  public int getInstanceCount() {
    return 0;
  }

  @Override
  public Context exceptionHandler(@Nullable Handler<Throwable> handler) {
    return null;
  }

  @Override
  public @Nullable Handler<Throwable> exceptionHandler() {
    return null;
  }

  @Override
  public boolean isEventLoopContext() {
    return false;
  }

  @Override
  public boolean isWorkerContext() {
    return false;
  }

  @Override
  public EventLoop nettyEventLoop() {
    return null;
  }

  @Override
  public <T> Future<T> executeBlocking(Callable<T> blockingCodeHandler, TaskQueue queue) {
    return null;
  }

  @Override
  public <T> Future<T> executeBlockingInternal(Callable<T> action) {
    return null;
  }

  @Override
  public <T> Future<T> executeBlockingInternal(Callable<T> action, boolean ordered) {
    return null;
  }

  @Override
  public Deployment getDeployment() {
    return null;
  }

  @Override
  public VertxInternal owner() {
    return impl;
  }

  @Override
  public <T> void emit(T argument, Handler<T> task) {

  }

  @Override
  public void execute(Runnable task) {

  }

  @Override
  public <T> void execute(T argument, Handler<T> task) {

  }

  @Override
  public void reportException(Throwable t) {

  }

  @Override
  public ConcurrentMap<Object, Object> contextData() {
    return null;
  }

  @Override
  public ConcurrentMap<Object, Object> localContextData() {
    return null;
  }

  @Override
  public ClassLoader classLoader() {
    return tccl;
  }

  @Override
  public WorkerPool workerPool() {
    return null;
  }

  @Override
  public VertxTracer tracer() {
    return null;
  }

  @Override
  public ContextInternal duplicate() {
    return null;
  }

  @Override
  public boolean isDeployment() {
    return false;
  }

  @Override
  public CloseFuture closeFuture() {
    return null;
  }
}
