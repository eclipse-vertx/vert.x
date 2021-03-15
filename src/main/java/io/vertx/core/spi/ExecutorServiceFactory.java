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

package io.vertx.core.spi;

import io.vertx.core.impl.VertxBuilder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * The interface for a factory used to obtain an external
 * {@code ExecutorService}.
 *
 * @author Gordon Hutchison
 */
public interface ExecutorServiceFactory extends VertxServiceProvider {

  /**
   * Default instance that delegates to {@link Executors#newFixedThreadPool(int, ThreadFactory)}
   */
  ExecutorServiceFactory INSTANCE = (threadFactory, concurrency, maxConcurrency) ->
    Executors.newFixedThreadPool(maxConcurrency, threadFactory);

  @Override
  default void init(VertxBuilder builder) {
    if (builder.executorServiceFactory() == null) {
      builder.executorServiceFactory(this);
    }
  }

  /**
   * Create an ExecutorService
   *
   * @param threadFactory  A {@link ThreadFactory} which must be used by the
   *                       created {@link ExecutorService} to create threads. Null
   *                       indicates there is no requirement to use a specific
   *                       factory.
   * @param concurrency    The target level of concurrency or 0 which indicates
   *                       unspecified
   * @param maxConcurrency A hard limit to the level of concurrency required,
   *                       should be greater than {@code concurrency} or 0 which
   *                       indicates unspecified.
   *
   * @return an {@link ExecutorService} that can be used to run tasks
   */
  ExecutorService createExecutor(ThreadFactory threadFactory, Integer concurrency, Integer maxConcurrency);

}
