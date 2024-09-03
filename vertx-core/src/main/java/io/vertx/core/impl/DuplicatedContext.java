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
import io.vertx.core.ThreadingModel;
import io.vertx.core.impl.deployment.Deployment;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.EventExecutor;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;

/**
 * A context that forwards most operations to a delegate. This context
 *
 * <ul>
 *  <li>maintains its own local data instead of the delegate.</li>
 * </ul>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
final class DuplicatedContext extends ContextBase implements ContextInternal {

  final ContextImpl delegate;

  DuplicatedContext(ContextImpl delegate, Object[] locals) {
    super(locals);
    this.delegate = delegate;
  }

  @Override
  public ThreadingModel threadingModel() {
    return delegate.threadingModel();
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
  public JsonObject config() {
    return delegate.config();
  }

  @Override
  public Context exceptionHandler(Handler<Throwable> handler) {
    delegate.exceptionHandler(handler);
    return this;
  }

  @Override
  public EventExecutor executor() {
    return delegate.executor();
  }

  @Override
  public Handler<Throwable> exceptionHandler() {
    return delegate.exceptionHandler();
  }

  @Override
  public EventLoop nettyEventLoop() {
    return delegate.nettyEventLoop();
  }

  @Override
  public Deployment getDeployment() {
    return delegate.getDeployment();
  }

  @Override
  public VertxInternal owner() {
    return delegate.owner();
  }

  @Override
  public ClassLoader classLoader() {
    return delegate.classLoader();
  }

  @Override
  public WorkerPool workerPool() {
    return delegate.workerPool();
  }

  @Override
  public void reportException(Throwable t) {
    delegate.reportException(t);
  }

  @Override
  public ConcurrentMap<Object, Object> contextData() {
    return delegate.contextData();
  }

  @Override
  public <T> Future<T> executeBlocking(Callable<T> blockingCodeHandler, boolean ordered) {
    return delegate.workerPool.executeBlocking(this, blockingCodeHandler, ordered ? delegate.orderedTasks : null);
  }

  @Override
  public boolean isEventLoopContext() {
    return delegate.isEventLoopContext();
  }

  @Override
  public boolean isWorkerContext() {
    return delegate.isWorkerContext();
  }

  @Override
  public ContextInternal duplicate() {
    return new DuplicatedContext(delegate, locals.length == 0 ? VertxImpl.EMPTY_CONTEXT_LOCALS : new Object[locals.length]);
  }

  @Override
  public ContextInternal unwrap() {
    return delegate;
  }

  @Override
  public boolean isDuplicate() {
    return true;
  }
}
