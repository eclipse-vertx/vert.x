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

import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class BenchmarkContext {

  private static final EventExecutor EXECUTOR = new EventExecutor() {
    @Override
    public boolean inThread() {
      throw new UnsupportedOperationException();
    }
    @Override
    public void execute(Runnable command) {
      command.run();
    }
  };

  public static ContextInternal create(Vertx vertx) {
    VertxImpl impl = (VertxImpl) vertx;
    return new ContextImpl(
      impl,
      0,
      ThreadingModel.WORKER,
      impl.getEventLoopGroup().next(),
      EXECUTOR,
      impl.internalWorkerPool,
      impl.workerPool,
      new TaskQueue(),
      null,
      null,
      Thread.currentThread().getContextClassLoader()
    );
  }
}
