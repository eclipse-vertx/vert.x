/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 and the Apache License, Version 2.0
 * which accompanies this distribution. The Eclipse Public License 2.0 is
 * available at http://www.eclipse.org/legal/epl-2.0.html, and the Apache
 * License, Version 2.0 is available at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl;

import io.vertx.core.spi.metrics.PoolMetrics;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class WorkerPool {

  private final ExecutorService pool;
  private final PoolMetrics metrics;

  WorkerPool(ExecutorService pool, PoolMetrics metrics) {
    this.pool = pool;
    this.metrics = metrics;
  }

  ExecutorService executor() {
    return pool;
  }

  PoolMetrics metrics() {
    return metrics;
  }

  void close() {
    if (metrics != null) {
      metrics.close();
    }
    pool.shutdownNow();
  }
}
