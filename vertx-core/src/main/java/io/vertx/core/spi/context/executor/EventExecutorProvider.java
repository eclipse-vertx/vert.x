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
package io.vertx.core.spi.context.executor;

import io.vertx.codegen.annotations.Unstable;
import io.vertx.core.internal.EventExecutor;
import io.vertx.core.internal.VertxBootstrap;
import io.vertx.core.spi.VertxServiceProvider;

/**
 * Event executor service provider interface.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Unstable
public interface EventExecutorProvider extends VertxServiceProvider {

  @Override
  default void init(VertxBootstrap builder) {
    if (builder.eventExecutorProvider() == null) {
      builder.eventExecutorProvider(this);
    }
  }

  /**
   * Give vertx an event executor for the given {@code thread}.
   *
   * @param thread the thread for which an executor is required
   * @return an event executor suitable for the given thread, tasks executed on this executor will be declared as
   * running on {@link io.vertx.core.ThreadingModel#OTHER}.
   */
  EventExecutor eventExecutorFor(Thread thread);

}
