/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
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
import io.vertx.core.impl.future.PromiseImpl;
import io.vertx.internal.core.*;
import io.vertx.internal.core.spi.context.AccessMode;
import io.vertx.internal.core.spi.context.ContextLocal;
import io.vertx.internal.core.spi.context.impl.ContextLocalImpl;
import io.vertx.core.impl.future.FailedFuture;
import io.vertx.core.impl.future.SucceededFuture;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Base class for context.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class ContextBase implements ContextInternal {

  final Object[] locals;

  protected ContextBase(Object[] locals) {
    this.locals = locals;
  }

  public <T> PromiseInternal<T> promise() {
    return new PromiseImpl<>(this);
  }

  public <T> Future<T> succeededFuture() {
    return new SucceededFuture<>(this, null);
  }

  public <T> Future<T> succeededFuture(T result) {
    return new SucceededFuture<>(this, result);
  }

  public <T> Future<T> failedFuture(Throwable failure) {
    return new FailedFuture<>(this, failure);
  }

  public <T> Future<T> failedFuture(String message) {
    return new FailedFuture<>(this, message);
  }

  public boolean isRunningOnContext() {
    return VertxImpl.currentContext() == this && inThread();
  }

  public ContextInternal beginDispatch() {
    VertxImpl vertx = (VertxImpl) owner();
    return vertx.beginDispatch(this);
  }

  public void endDispatch(ContextInternal previous) {
    VertxImpl vertx = (VertxImpl) owner();
    vertx.endDispatch((ContextBase) previous);
  }

  public final <T> T getLocal(ContextLocal<T> key, AccessMode accessMode) {
    ContextLocalImpl<T> internalKey = (ContextLocalImpl<T>) key;
    int index = internalKey.index;
    if (index >= locals.length) {
      throw new IllegalArgumentException();
    }
    Object res = accessMode.get(locals, index);
    return (T) res;
  }

  public final <T> T getLocal(ContextLocal<T> key, AccessMode accessMode, Supplier<? extends T> initialValueSupplier) {
    ContextLocalImpl<T> internalKey = (ContextLocalImpl<T>) key;
    int index = internalKey.index;
    if (index >= locals.length) {
      throw new IllegalArgumentException("Invalid key index: " + index);
    }
    Object res = accessMode.getOrCreate(locals, index, (Supplier<Object>) initialValueSupplier);
    return (T) res;
  }

  public final <T> void putLocal(ContextLocal<T> key, AccessMode accessMode, T value) {
    ContextLocalImpl<T> internalKey = (ContextLocalImpl<T>) key;
    int index = internalKey.index;
    if (index >= locals.length) {
      throw new IllegalArgumentException();
    }
    accessMode.put(locals, index, value);
  }

  public long setPeriodic(long delay, Handler<Long> handler) {
    VertxImpl owner = (VertxImpl) owner();
    return owner.scheduleTimeout(this, true, delay, TimeUnit.MILLISECONDS, false, handler);
  }

  public long setTimer(long delay, Handler<Long> handler) {
    VertxImpl owner = (VertxImpl) owner();
    return owner.scheduleTimeout(this, false, delay, TimeUnit.MILLISECONDS, false, handler);
  }

  public Timer timer(long delay, TimeUnit unit) {
    Objects.requireNonNull(unit);
    if (delay <= 0) {
      throw new IllegalArgumentException("Invalid timer delay: " + delay);
    }
    io.netty.util.concurrent.ScheduledFuture<Void> fut = nettyEventLoop().schedule(() -> null, delay, unit);
    TimerImpl timer = new TimerImpl(this, fut);
    fut.addListener(timer);
    return timer;
  }

  public boolean isDeployment() {
    return getDeployment() != null;
  }

  public String deploymentID() {
    Deployment deployment = getDeployment();
    return deployment != null ? deployment.deploymentID() : null;
  }

  public int getInstanceCount() {
    Deployment deployment = getDeployment();

    // the no verticle case
    if (deployment == null) {
      return 0;
    }

    // the single verticle without an instance flag explicitly defined
    if (deployment.deploymentOptions() == null) {
      return 1;
    }
    return deployment.deploymentOptions().getInstances();
  }

  /**
   * @return the deployment associated with this context or {@code null}
   */
  public abstract Deployment getDeployment();

  /**
   * @return the context worker pool
   */
  public abstract WorkerPool workerPool();

  public abstract CloseFuture closeFuture();

  public void addCloseHook(Closeable hook) {
    closeFuture().add(hook);
  }

  public void removeCloseHook(Closeable hook) {
    closeFuture().remove(hook);
  }

  /**
   * Like {@link #executeBlocking(Callable, boolean)} but uses the {@code queue} to order the tasks instead
   * of the internal queue of this context.
   */
  public abstract <T> Future<T> executeBlocking(Callable<T> blockingCodeHandler, TaskQueue queue);

  @Override
  public ContextInternal asEventLoopContext() {
    if (isEventLoopContext()) {
      return this;
    } else {
      return owner().createEventLoopContext(nettyEventLoop(), workerPool(), classLoader());
    }
  }
}
