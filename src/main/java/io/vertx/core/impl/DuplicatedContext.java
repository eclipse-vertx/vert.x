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
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A context that forwards most operations to a delegate. This context
 *
 * <ul>
 *   <li>maintains its own ordered task queue, ordered execute blocking are ordered on this
 *  context instead of the delegate.</li>
 *  <li>maintains its own local data instead of the delegate.</li>
 * </ul>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class DuplicatedContext extends AbstractContext {

  protected final ContextImpl delegate;
  private ConcurrentMap<Object, Object> localData;

  DuplicatedContext(ContextImpl delegate) {
    this.delegate = delegate;
  }

  @Override
  boolean inThread() {
    return delegate.inThread();
  }

  @Override
  public final CloseFuture closeFuture() {
    return delegate.closeFuture();
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
  public final void reportException(Throwable t) {
    delegate.reportException(t);
  }

  @Override
  public final ConcurrentMap<Object, Object> contextData() {
    return delegate.contextData();
  }

  @Override
  public final ConcurrentMap<Object, Object> localContextData() {
    synchronized (this) {
      if (localData == null) {
        localData = new ConcurrentHashMap<>();
      }
      return localData;
    }
  }

  @Override
  public final <T> Future<T> executeBlockingInternal(Handler<Promise<T>> action) {
    return ContextImpl.executeBlocking(this, action, delegate.internalBlockingPool, delegate.internalOrderedTasks);
  }

  @Override
  public final <T> Future<T> executeBlockingInternal(Handler<Promise<T>> action, boolean ordered) {
    return ContextImpl.executeBlocking(this, action, delegate.internalBlockingPool, ordered ? delegate.internalOrderedTasks : null);
  }

  @Override
  public final <T> Future<T> executeBlocking(Handler<Promise<T>> action, boolean ordered) {
    return ContextImpl.executeBlocking(this, action, delegate.workerPool, ordered ? delegate.orderedTasks : null);
  }

  @Override
  public final <T> Future<T> executeBlocking(Handler<Promise<T>> blockingCodeHandler, TaskQueue queue) {
    return ContextImpl.executeBlocking(this, blockingCodeHandler, delegate.workerPool, queue);
  }

  @Override
  public final void runOnContext(Handler<Void> action) {
    delegate.runOnContext(this, action);
  }

  @Override
  public final <T> void execute(T argument, Handler<T> task) {
    delegate.execute(this, argument, task);
  }

  @Override
  public <T> void emit(T argument, Handler<T> task) {
    delegate.emit(this, argument, task);
  }

  @Override
  public void execute(Runnable task) {
    delegate.execute(this, task);
  }

  @Override
  public boolean isEventLoopContext() {
    return delegate.isEventLoopContext();
  }

  @Override
  public ContextInternal duplicate() {
    return new DuplicatedContext(delegate);
  }

}
