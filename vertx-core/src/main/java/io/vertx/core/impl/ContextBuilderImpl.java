/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
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
import io.vertx.core.ThreadingModel;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.ContextBuilder;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.WorkerPool;
import io.vertx.core.internal.deployment.DeploymentContext;

import java.util.Objects;

import static io.vertx.core.ThreadingModel.EVENT_LOOP;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ContextBuilderImpl implements ContextBuilder {

  private final VertxImpl vertx;
  private ThreadingModel threadingModel;
  private EventLoop eventLoop;
  private ClassLoader classLoader;
  private CloseFuture closeFuture;
  private WorkerPool workerPool;
  private DeploymentContext deploymentContext;

  ContextBuilderImpl(VertxImpl vertx) {
    this.vertx = Objects.requireNonNull(vertx);
    this.threadingModel = EVENT_LOOP;
  }

  @Override
  public ContextBuilderImpl withThreadingModel(ThreadingModel threadingModel) {
    this.threadingModel = Objects.requireNonNull(threadingModel);
    return this;
  }

  @Override
  public ContextBuilderImpl withEventLoop(EventLoop eventLoop) {
    this.eventLoop = eventLoop;
    return this;
  }

  @Override
  public ContextBuilderImpl withClassLoader(ClassLoader classLoader) {
    this.classLoader = classLoader;
    return this;
  }

  @Override
  public ContextBuilderImpl withCloseFuture(CloseFuture closeFuture) {
    this.closeFuture = closeFuture;
    return this;
  }

  @Override
  public ContextBuilderImpl withWorkerPool(WorkerPool workerPool) {
    this.workerPool = workerPool;
    return this;
  }

  public ContextBuilderImpl withDeploymentContext(DeploymentContext deploymentContext) {
    this.deploymentContext = deploymentContext;
    return this;
  }

  @Override
  public ContextInternal build() {
    EventLoop eventLoop = this.eventLoop;
    if (eventLoop == null) {
      eventLoop = vertx.nettyEventLoopGroup().next();
    }
    CloseFuture closeFuture = this.closeFuture;
    if (closeFuture == null) {
      closeFuture = vertx.closeFuture();
    }
    ClassLoader classLoader = this.classLoader;
    if (classLoader == null) {
      classLoader = Thread.currentThread().getContextClassLoader();
    }
    return vertx.createContext(threadingModel, eventLoop, closeFuture, workerPool, deploymentContext, classLoader);
  }
}
