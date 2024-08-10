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
   * Signals a request is enqueued to obtain a resource.
   */
  default Q enqueue() {
    return null;
  }

  /**
   * Signals the request was removed from the queue.
   */
  default void dequeue(Q queueMetric) {
  }

  /**
   * Signal the beginning of the utilisation of a pool resource.
   *
   * @return the timer measuring the resource utilisation
   */
  default T begin() {
    return null;
  }

  /**
   * Signal the release of a pool resource.
   *
   * @param t the timer measuring the resource utilisation returned by {@link #begin}
   */
  default void end(T t) {
  }
}
