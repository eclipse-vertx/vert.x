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

package io.vertx.core.spi.metrics;

/**
 * An SPI used internally by Vert.x to gather metrics on pools used by Vert.x (execute blocking, worker verticle or data source).
 * <p>
 * These metrics measure the latency of a task queuing.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public interface QueueMetrics<Q> extends Metrics {

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
}
