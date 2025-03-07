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

import io.vertx.core.Handler;
import io.vertx.core.internal.*;
import io.vertx.core.spi.context.storage.AccessMode;
import io.vertx.core.spi.context.storage.ContextLocal;

import java.util.function.Supplier;

/**
 * Base class for context.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class ContextBase implements ContextInternal {

  final Object[] locals;

  ContextBase(Object[] locals) {
    this.locals = locals;
  }

  public ContextInternal beginDispatch() {
    VertxImpl vertx = owner();
    return vertx.beginDispatch(this);
  }

  public void endDispatch(ContextInternal previous) {
    VertxImpl vertx = owner();
    vertx.endDispatch(previous);
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

  @Override
  public final boolean inThread() {
    return executor().inThread();
  }

  @Override
  public final <T> void emit(T argument, Handler<T> task) {
    if (executor().inThread()) {
      ContextInternal prev = beginDispatch();
      try {
        task.handle(argument);
      } catch (Throwable t) {
        reportException(t);
      } finally {
        endDispatch(prev);
      }
    } else {
      executor().execute(() -> emit(argument, task));
    }
  }

  @Override
  public final void execute(Runnable task) {
    if (executor().inThread()) {
      task.run();
    } else {
      executor().execute(task);
    }
  }

  /**
   * <ul>
   *   <li>When the current thread is event-loop thread of this context the implementation will execute the {@code task} directly</li>
   *   <li>When the current thread is a worker thread of this context the implementation will execute the {@code task} directly</li>
   *   <li>Otherwise the task will be scheduled on the context thread for execution</li>
   * </ul>
   */
  @Override
  public final <T> void execute(T argument, Handler<T> task) {
    if (executor().inThread()) {
      task.handle(argument);
    } else {
      executor().execute(() -> task.handle(argument));
    }
  }

  @Override
  public abstract VertxImpl owner();

  @Override
  public ContextBuilder toBuilder() {
      return new ContextBuilderImpl(owner())
        .withCloseFuture(closeFuture())
        .withEventLoop(nettyEventLoop())
        .withThreadingModel(threadingModel())
        .withDeploymentContext(deployment())
        .withWorkerPool(workerPool())
        .withClassLoader(classLoader());
    }
}
