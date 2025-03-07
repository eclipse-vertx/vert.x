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
package io.vertx.core.internal;

import io.netty.channel.EventLoop;
import io.vertx.core.ThreadingModel;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface ContextBuilder {

  /**
   * Override the {@link ThreadingModel#EVENT_LOOP} with the given {@code threadingModel}.
   *
   * @param threadingModel the override
   * @return this fluent object
   */
  ContextBuilder withThreadingModel(ThreadingModel threadingModel);

  /**
   * Set the {@code eventLoop} to use, when none is specified an event-loop will be assigned
   * from the Vert.x event-loop group.
   *
   * @return this fluent object
   */
  ContextBuilder withEventLoop(EventLoop eventLoop);

  /**
   * Set the {@code classLoader} to use, when none is specified the default thread context class loader
   * is used.
   *
   * @return this fluent object
   */
  ContextBuilder withClassLoader(ClassLoader classLoader);

  /**
   * Set the {@code closeFuture} to use, when none is specified the Vert.x close future is used.
   *
   * @return this fluent object
   */
  ContextBuilder withCloseFuture(CloseFuture closeFuture);

  /**
   * Set the {@code workerPool} to use, when none is specified, the default worker pool is assigned.
   *
   * @return this fluent object
   */
  ContextBuilder withWorkerPool(WorkerPool workerPool);

  /**
   * @return a context using the resources specified in this builder
   */
  ContextInternal build();

}
