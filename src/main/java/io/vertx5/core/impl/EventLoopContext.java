package io.vertx5.core.impl;

import io.netty5.channel.EventLoop;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.impl.CloseFuture;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.Deployment;
import io.vertx.core.impl.TaskQueue;
import io.vertx.core.impl.WorkerPool;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx5.core.Vertx;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

public class EventLoopContext implements ContextInternal {

  private final Vertx vertx;
  private final EventLoop eventLoop;
  private final Executor executor;

  public EventLoopContext(Vertx vertx, EventLoop eventLoop) {

    Executor executor = r -> {
      eventLoop.execute(() -> {
        r.run();
      });
    };

    this.executor = executor;
    this.vertx = vertx;
    this.eventLoop = eventLoop;
  }

  @Override
  public ContextInternal beginDispatch() {
    VertxThread thread = (VertxThread) Thread.currentThread();
    ContextInternal previous = thread.context;
    thread.context = this;
    return previous;
  }

  @Override
  public void endDispatch(ContextInternal previous) {
    VertxThread thread = (VertxThread) Thread.currentThread();
    thread.context = previous;
  }

  @Override
  public <T> Future<@Nullable T> executeBlocking(Handler<Promise<T>> blockingCodeHandler, boolean ordered) {
    return null;
  }

  @Override
  public @Nullable JsonObject config() {
    return null;
  }

  @Override
  public boolean isEventLoopContext() {
    return true;
  }

  @Override
  public boolean isWorkerContext() {
    return false;
  }

  @Override
  public Vertx owner() {
    return vertx;
  }

  @Override
  public Context exceptionHandler(@Nullable Handler<Throwable> handler) {
    return this;
  }

  @Override
  public @Nullable Handler<Throwable> exceptionHandler() {
    return null;
  }

  @Override
  public Executor executor() {
    return executor;
  }

  @Override
  public EventLoop nettyEventLoop() {
    return eventLoop;
  }

  @Override
  public <T> Future<T> executeBlocking(Handler<Promise<T>> blockingCodeHandler, TaskQueue queue) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> Future<T> executeBlockingInternal(Handler<Promise<T>> action) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> Future<T> executeBlockingInternal(Handler<Promise<T>> action, boolean ordered) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Deployment getDeployment() {
    return null;
  }

  @Override
  public boolean inThread() {
    return eventLoop.inEventLoop();
  }

  @Override
  public <T> void emit(T argument, Handler<T> task) {
    if (eventLoop.inEventLoop()) {
      ContextInternal prev = beginDispatch();
      try {
        task.handle(argument);
      } catch (Throwable t) {
        reportException(t);
      } finally {
        endDispatch(prev);
      }
    } else {
      eventLoop.execute(() -> emit(argument, task));
    }
  }

  @Override
  public void execute(Runnable task) {
    if (eventLoop.inEventLoop()) {
      task.run();
    } else {
      eventLoop.execute(task);
    }
  }

  @Override
  public <T> void execute(T argument, Handler<T> task) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void reportException(Throwable t) {
    t.printStackTrace();
  }

  @Override
  public ConcurrentMap<Object, Object> contextData() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ConcurrentMap<Object, Object> localContextData() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ClassLoader classLoader() {
    return null;
  }

  @Override
  public WorkerPool workerPool() {
    throw new UnsupportedOperationException();
  }

  @Override
  public VertxTracer tracer() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ContextInternal duplicate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public CloseFuture closeFuture() {
    throw new UnsupportedOperationException();
  }
}
