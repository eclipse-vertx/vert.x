/*
 *  Copyright (c) 2011-2015 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *       The Eclipse Public License is available at
 *       http://www.eclipse.org/legal/epl-v10.html
 *
 *       The Apache License v2.0 is available at
 *       http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.spi.metrics;

/**
 * An SPI used internally by Vert.x to gather metrics on a worker thread (execute blocking, worker verticle).
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public interface ThreadPoolMetrics<T> extends Metrics {

  /**
   * A new task has been submitted to the worker queue.
   * This method is called from the submitter context.
   *
   * @return the submitted task.
   */
  T taskSubmitted();

  /**
   * The task has been rejected. The underlying thread pool has probably be shutdown.
   */
  void taskRejected(T task);

  /**
   * The submitted task start its execution.
   */
  void taskExecuting(T task);

  /**
   * The submitted tasks has completed its execution.
   *
   * @param succeeded whether or not the task has gracefully completed
   */
  void taskCompleted(T task, boolean succeeded);

}