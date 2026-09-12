/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.*;
import io.vertx.core.internal.*;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;

import java.lang.ref.Cleaner;
import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class WorkerExecutorImpl extends CleanableResource<WorkerPool> implements MetricsProvider, WorkerExecutorInternal {

  private final VertxInternal vertx;

  public WorkerExecutorImpl(VertxInternal vertx, Cleaner cleaner, CloseableResource<WorkerPool> resource) {
    super(cleaner, resource);
    this.vertx = vertx;
  }

  @Override
  public Metrics getMetrics() {
    return getOrDie().metrics();
  }

  @Override
  public boolean isMetricsEnabled() {
    return getMetrics() != null;
  }

  @Override
  public Vertx vertx() {
    return vertx;
  }

  @Override
  public WorkerPool pool() {
    return getOrDie();
  }

  @Override
  public <T> Future<@Nullable T> executeBlocking(Callable<T> blockingCodeHandler, boolean ordered) {
    ContextInternal context = vertx.getOrCreateContext();
    TaskQueue orderedTasks;
    if (ordered) {
      if (context instanceof ShadowContext) {
        orderedTasks = ((ShadowContext)context).orderedTasks;
      } else {
        ContextImpl impl = context instanceof DuplicatedContext ? ((DuplicatedContext)context).delegate : (ContextImpl) context;
        orderedTasks = impl.executeBlockingTasks;
      }
    } else {
      orderedTasks = null;
    }
    return ExecuteBlocking.executeBlocking(getOrDie(), context, blockingCodeHandler, orderedTasks);
  }

  @Override
  public Future<Void> close() {
    return shutdown(Duration.ZERO);
  }
}
