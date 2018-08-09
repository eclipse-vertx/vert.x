/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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
 * An SPI used internally by Vert.x to gather metrics on pools used by Vert.x  (execute blocking, worker verticle or data source).
 * <p>
 * Usually these metrics measure the latency of a task queuing and the latency a task execution.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public interface PoolMetrics<T> extends Metrics {

  /**
   * A new task has been submitted to access the resource.
   * This method is called from the submitter context.
   *
   * @return the timer measuring the task queuing
   */
  default T submitted() {
    return null;
  }

  /**
   * The submitted task start to use the resource.
   *
   * @param t the timer measuring the task queuing returned by {@link #submitted()}
   * @return the timer measuring the task execution
   */
  default T begin(T t) {
    return null;
  }

  /**
   * The task has been rejected. The underlying resource has probably be shutdown.
   *
   * @param t the timer measuring the task queuing returned by {@link #submitted()}
   */
  default void rejected(T t) {
  }

  /**
   * The submitted tasks has completed its execution and release the resource.
   *
   * @param succeeded whether or not the task has gracefully completed
   * @param t the timer measuring the task execution returned by {@link #begin}
   */
  default void end(T t, boolean succeeded) {
  }
}
