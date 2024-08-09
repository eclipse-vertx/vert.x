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
package io.vertx.core.spi.metrics;

/**
 * Worker pool metrics
 */
public interface PoolMetrics<Q, T> extends Metrics {

  /**
   * Called when a resource is requested.
   */
  default Q enqueue() {
    return null;
  }

  /**
   * Called when a request for connection is satisfied.
   */
  default void dequeue(Q queueMetric) {
  }

  /**
   * Begin the execution of a task.
   *
   * @return the timer measuring the task execution
   */
  default T begin() {
    return null;
  }

  /**
   * The submitted tasks has completed its execution and release the resource.
   *
   * @param t the timer measuring the task execution returned by {@link #begin}
   */
  default void end(T t) {
  }
}
