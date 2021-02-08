/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
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
import io.vertx.core.Closeable;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * A context that maintains separate local data and forwards all operations to a delegate.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
final class DuplicatedContext extends AbstractContext {

  private final AbstractContext delegate;
  private ConcurrentMap<Object, Object> localData;

  DuplicatedContext(AbstractContext delegate) {
    this.delegate = delegate;
  }

  @Override
  public final TaskQueue orderedTasks() {
    return this.delegate.orderedTasks();
  }

  @Override
  public final TaskQueue orderedTasksInternal() {
    return this.delegate.orderedTasksInternal();
  }

  @Override
  boolean inThread() {
    return delegate.inThread();
  }

  @Override
  public final CloseHooks closeHooks() {
    return delegate.closeHooks();
  }

  @Override
  public final boolean isDeployment() {
    return delegate.isDeployment();
  }

  @Override
  public final VertxTracer tracer() {
    return delegate.tracer();
  }

  @Override
  public final String deploymentID() {
    return delegate.deploymentID();
  }

  @Override
  public final JsonObject config() {
    return delegate.config();
  }

  @Override
  public final int getInstanceCount() {
    return delegate.getInstanceCount();
  }

  @Override
  public final Context exceptionHandler(Handler<Throwable> handler) {
    delegate.exceptionHandler(handler);
    return this;
  }

  @Override
  public final Handler<Throwable> exceptionHandler() {
    return delegate.exceptionHandler();
  }

  @Override
  public final void addCloseHook(Closeable hook) {
    delegate.addCloseHook(hook);
  }

  @Override
  public final void removeCloseHook(Closeable hook) {
    delegate.removeCloseHook(hook);
  }

  @Override
  public final EventLoop nettyEventLoop() {
    return delegate.nettyEventLoop();
  }

  @Override
  public final Deployment getDeployment() {
    return delegate.getDeployment();
  }

  @Override
  public final VertxInternal owner() {
    return delegate.owner();
  }

  @Override
  public final ClassLoader classLoader() {
    return delegate.classLoader();
  }

  @Override
  public WorkerPool workerPool() {
    return delegate.workerPool();
  }

  @Override
  public WorkerPool workerPoolInternal() {
    return this.delegate.workerPoolInternal();
  }

  @Override
  public final void reportException(Throwable t) {
    delegate.reportException(t);
  }

  @Override
  public final ConcurrentMap<Object, Object> contextData() {
    return delegate.contextData();
  }

  @Override
  public final synchronized ConcurrentMap<Object, Object> localContextData() {
    if(localData == null) {
      localData = new ConcurrentHashMap<>();
    }
    return localData;
  }

  @Override
  public final <T> Future<T> executeBlockingInternal(Handler<Promise<T>> action) {
    return ContextImpl.executeBlocking(this, action, delegate.workerPoolInternal(), delegate.orderedTasksInternal());
  }

  @Override
  public final <T> Future<T> executeBlockingInternal(Handler<Promise<T>> action, boolean ordered) {
    return ContextImpl.executeBlocking(this, action, delegate.workerPoolInternal(), ordered ? delegate.orderedTasksInternal() : null);
  }

  @Override
  public final <T> Future<T> executeBlocking(Handler<Promise<T>> action, boolean ordered) {
    return ContextImpl.executeBlocking(this, action, delegate.workerPool(), ordered ? this.orderedTasks() : null);
  }

  @Override
  public final <T> Future<T> executeBlocking(Handler<Promise<T>> blockingCodeHandler, TaskQueue queue) {
    return ContextImpl.executeBlocking(this, blockingCodeHandler, delegate.workerPool(), queue);
  }

  @Override
  public final void runOnContext(Handler<Void> action) {
    delegate.runOnContext(this, action);
  }

  @Override
  void runOnContext(AbstractContext ctx, Handler<Void> action) {
    delegate.runOnContext(ctx, action);
  }

  @Override
  public final <T> void execute(T argument, Handler<T> task) {
    delegate.execute(this, argument, task);
  }

  @Override
  <T> void execute(AbstractContext ctx, T argument, Handler<T> task) {
    delegate.execute(ctx, argument, task);
  }

  @Override
  public <T> void emit(T argument, Handler<T> task) {
    delegate.emit(this, argument, task);
  }

  @Override
  <T> void emit(AbstractContext ctx, T argument, Handler<T> task) {
    delegate.emit(ctx, argument, task);
  }

  @Override
  public void execute(Runnable task) {
    delegate.execute(this, task);
  }

  @Override
  <T> void execute(AbstractContext ctx, Runnable task) {
    delegate.execute(ctx, task);
  }

  @Override
  public final boolean isEventLoopContext() {
    return delegate.isEventLoopContext();
  }

  @Override
  public final ContextInternal duplicate() {
    return new DuplicatedContext(this.delegate);
  }

  @Override
  public final ContextInternal substitute(Function<ContextInternal, ContextSubstitution> builder) {
    return new SubstitutedContext(this.delegate, builder);
  }

  @Override
  public final ContextInternal substituteParent() {
    return this.delegate.substituteParent();
  }

  @Override
  public final ContextInternal duplicateDelegate() {
    return this.delegate;
  }

  @Override
  final AbstractContext root() {
    return this.delegate.root();
  }
}
