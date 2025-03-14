/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
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
import io.vertx.core.*;
import io.vertx.core.Future;
import io.vertx.core.impl.deployment.DeploymentContext;
import io.vertx.core.internal.EventExecutor;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.concurrent.*;

/**
 * A base class for {@link Context} implementations.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public final class ContextImpl extends ContextBase implements ContextInternal {

  private static final Logger log = LoggerFactory.getLogger(ContextImpl.class);

  static final boolean DISABLE_TIMINGS = SysProps.DISABLE_CONTEXT_TIMINGS.getBoolean();

  private final VertxImpl owner;
  private final JsonObject config;
  private final DeploymentContext deployment;
  private final CloseFuture closeFuture;
  private final ClassLoader tccl;
  private final EventLoopExecutor eventLoop;
  private final ThreadingModel threadingModel;
  private final EventExecutor executor;
  private ConcurrentMap<Object, Object> data;
  private volatile Handler<Throwable> exceptionHandler;
  final WorkerPool workerPool;
  final WorkerTaskQueue executeBlockingTasks;

  public ContextImpl(VertxImpl vertx,
                     Object[] locals,
                     EventLoopExecutor eventLoop,
                     ThreadingModel threadingModel,
                     EventExecutor executor,
                     WorkerPool workerPool,
                     DeploymentContext deployment,
                     CloseFuture closeFuture,
                     ClassLoader tccl) {
    super(locals);
    JsonObject config = null;
    if (deployment != null) {
      config = deployment.deployment().options().getConfig();
    }
    if (config == null) {
      config = new JsonObject();
    }
    this.deployment = deployment;
    this.config = config;
    this.eventLoop = eventLoop;
    this.threadingModel = threadingModel;
    this.executor = executor;
    this.tccl = tccl;
    this.owner = vertx;
    this.workerPool = workerPool;
    this.closeFuture = closeFuture;
    this.executeBlockingTasks = new WorkerTaskQueue();
  }

  public Future<Void> close() {
    Future<Void> fut;
    if (closeFuture == owner.closeFuture()) {
      fut = Future.succeededFuture();
    } else {
      fut = closeFuture.close();
    }
    fut = fut.eventually(() -> Future.<Void>future(p -> executeBlockingTasks.shutdown(eventLoop.eventLoop, p)));
    if (executor instanceof WorkerExecutor) {
      WorkerExecutor workerExec = (WorkerExecutor) executor;
      fut = fut.eventually(() -> Future.<Void>future(p -> workerExec.taskQueue().shutdown(eventLoop.eventLoop, p)));
    }
    return fut;
  }

  public DeploymentContext deployment() {
    return deployment;
  }

  @Override
  public CloseFuture closeFuture() {
    return closeFuture;
  }

  @Override
  public JsonObject config() {
    return config;
  }

  public EventLoop nettyEventLoop() {
    return eventLoop.eventLoop;
  }

  public VertxImpl owner() {
    return owner;
  }

  @Override
  public <T> Future<T> executeBlocking(Callable<T> blockingCodeHandler, boolean ordered) {
    return workerPool.executeBlocking(this, blockingCodeHandler, ordered ? executeBlockingTasks : null);
  }

  @Override
  public EventExecutor eventLoop() {
    return eventLoop;
  }

  @Override
  public EventExecutor executor() {
    return executor;
  }

  @Override
  public boolean isEventLoopContext() {
    return threadingModel() == ThreadingModel.EVENT_LOOP;
  }

  @Override
  public boolean isWorkerContext() {
    return threadingModel() == ThreadingModel.WORKER;
  }

  public ThreadingModel threadingModel() {
    return threadingModel;
  }

  @Override
  public VertxTracer tracer() {
    return owner.tracer();
  }

  @Override
  public ClassLoader classLoader() {
    return tccl;
  }

  @Override
  public WorkerPool workerPool() {
    return workerPool;
  }

  @Override
  public synchronized ConcurrentMap<Object, Object> contextData() {
    if (data == null) {
      data = new ConcurrentHashMap<>();
    }
    return data;
  }

  public void reportException(Throwable t) {
    Handler<Throwable> handler = exceptionHandler;
    if (handler == null) {
      handler = owner.exceptionHandler();
    }
    if (handler != null) {
      handler.handle(t);
    } else {
      log.error("Unhandled exception", t);
    }
  }

  @Override
  public Context exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }

  @Override
  public Handler<Throwable> exceptionHandler() {
    return exceptionHandler;
  }

  @Override
  public ContextInternal duplicate() {
    return new DuplicatedContext(this, locals.length == 0 ? VertxImpl.EMPTY_CONTEXT_LOCALS : new Object[locals.length]);
  }
}
