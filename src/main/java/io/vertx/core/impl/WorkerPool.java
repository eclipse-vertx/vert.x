/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.impl;

import io.vertx.core.spi.metrics.PoolMetrics;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class WorkerPool {

  private final OrderedExecutorFactory orderedFact;
  private final ExecutorService pool;
  private final PoolMetrics metrics;

  public WorkerPool(ExecutorService pool, PoolMetrics metrics) {
    this.orderedFact = new OrderedExecutorFactory(pool);
    this.pool = pool;
    this.metrics = metrics;
  }

  ExecutorService executor() {
    return pool;
  }

  Executor createOrderedExecutor() {
    return orderedFact.getExecutor();
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
