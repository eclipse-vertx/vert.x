/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
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
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.*;
import io.vertx.core.internal.WorkerPool;
import io.vertx.core.internal.deployment.DeploymentContext;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.EventExecutor;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;

/**
 * <p>A shadow context represents a context from a different Vert.x instance than the {@link #owner} instance.</p>
 *
 * <p>The primary use case for shadow context, is the integration of a Vert.x client within a Vert.x server with
 * two distinct instances of Vert.x.</p>
 *
 * <p>When {@link Vertx#getOrCreateContext()} is invoked on an instance and the current thread is associated with a context
 * of another instance, a shadow context is returned with an event-loop thread of the called vertx instance.</p>
 *
 * <p>When a thread is associated with a shadow context, {@link Vertx#getOrCreateContext()} returns the context tha
 * was created by the corresponding instance.</p>
 *
 * <p>The following can be expected of a shadow context
 * <ul>
 *   <li>{@link #threadingModel()} returns the {@link ThreadingModel#OTHER}</li>
 *   <li>{@link #nettyEventLoop()} returns an event-loop of the {@link #owner()}</li>
 *   <li>{@link #owner()} returns the Vertx instance that created it</li>
 *   <li>{@link #executor()} returns the event executor of the shadowed context</li>
 *   <li>{@link #workerPool()} returns the {@link #owner()} worker pool</li>
 * </ul>
 * </p>
 *
 * <p>When a task is scheduled on a shadow context, that task is scheduled on the actual context event executor, therefore
 * middleware running on the actual context interacts with this context resources, middleware running on the shadow context
 * interacts with the shadow context resources.</p>
 */
public final class ShadowContext extends ContextBase {

  final VertxImpl owner;
  final ContextBase delegate;
  private final EventLoopExecutor eventLoop;
  final TaskQueue orderedTasks;

  ShadowContext(VertxImpl owner, EventLoopExecutor eventLoop, ContextInternal delegate) {
    super(((ContextBase)delegate).locals);
    this.owner = owner;
    this.eventLoop = eventLoop;
    this.delegate = (ContextBase) delegate;
    this.orderedTasks = new TaskQueue();
  }

  public ContextInternal delegate() {
    return delegate;
  }

  @Override
  public EventExecutor eventLoop() {
    return eventLoop;
  }

  @Override
  public EventExecutor executor() {
    return delegate.executor();
  }

  @Override
  public EventLoop nettyEventLoop() {
    return eventLoop.eventLoop;
  }

  @Override
  public DeploymentContext deployment() {
    return null;
  }

  @Override
  public VertxImpl owner() {
    return owner;
  }

  @Override
  public void reportException(Throwable t) {
    // Not sure of that
    delegate.reportException(t);
  }

  @Override
  public ConcurrentMap<Object, Object> contextData() {
    return delegate.contextData();
  }

  @Override
  public ClassLoader classLoader() {
    return delegate.classLoader();
  }

  @Override
  public WorkerPool workerPool() {
    return owner.workerPool();
  }

  @Override
  public VertxTracer tracer() {
    return delegate.tracer();
  }

  @Override
  public ContextInternal duplicate() {
    return new ShadowContext(owner, eventLoop, delegate.duplicate());
  }

  @Override
  public ContextInternal unwrap() {
    if (isDuplicate()) {
      return new ShadowContext(owner, eventLoop, delegate.unwrap());
    } else {
      return this;
    }
  }

  @Override
  public boolean isDuplicate() {
    return delegate.isDuplicate();
  }

  @Override
  public CloseFuture closeFuture() {
    return owner.closeFuture();
  }

  @Override
  public Future<Void> close() {
    return Future.succeededFuture();
  }

  @Override
  public <T> Future<@Nullable T> executeBlocking(Callable<T> blockingCodeHandler, boolean ordered) {
    return ExecuteBlocking.executeBlocking(owner.workerPool(), this, blockingCodeHandler, ordered ? orderedTasks : null);
  }

  @Override
  public @Nullable JsonObject config() {
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
  public ThreadingModel threadingModel() {
    return ThreadingModel.OTHER;
  }

  @Override
  public Context exceptionHandler(@Nullable Handler<Throwable> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @Nullable Handler<Throwable> exceptionHandler() {
    throw new UnsupportedOperationException();
  }
}
