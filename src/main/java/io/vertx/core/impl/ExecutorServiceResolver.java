/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.vertx.core.ServiceHelper;
import io.vertx.core.VertxOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.executor.ExecutorServiceFactory;

public class ExecutorServiceResolver {

  private static final Logger log = LoggerFactory.getLogger(ExecutorServiceResolver.class);
  private ExecutorServiceFactory spiExecutorServiceFactory;

  /**
   * We resolve any SPI ExecutorServiceFactory implementation at an instance level
   * rather than statically or in the get methods to enable the SPI
   * implementations that are on the classpath to be re-checked on creation of a
   * new instance (for example in testing) but only checked once in normal
   * operation.
   */
  ExecutorServiceResolver() {
    this.spiExecutorServiceFactory = ServiceHelper.loadFactoryOrNull(ExecutorServiceFactory.class);
    log.debug("External SPI ExecutorServiceFactory was " + this.spiExecutorServiceFactory);
  }

  /**
   * This method returns an ExecutorService created using ServiceLoadable
   * ExecutorServiceFactory or if none is present one based on a
   * {@link ThreadPoolExecutor} in either case {@link VertxThreadFactory} is used
   * as a source of threads.
   * 
   * @param name               used as a Thread name prefix
   * @param poolSize           used as the thread pool size
   * @param maxExecuteTime     the time a task can run before a blocked thread
   *                           warning, used by {@code checker}
   * @param maxExecuteTimeUnit the time periods for the maxExecuteTime parameter
   * @param checker            used to monitor task completion times
   * @return either an internal or SPI provided {@link ExecutorService}
   */
  ExecutorService getExecutorServiceForSharedWorkerPool(String name, int poolSize, long maxExecuteTime, TimeUnit maxExecuteTimeUnit,
      BlockedThreadChecker checker) {

    VertxThreadFactory threadFactory = new VertxThreadFactory(name + "-", checker, true, maxExecuteTime, maxExecuteTimeUnit);
    ExecutorService es;
    if (spiExecutorServiceFactory != null) {
      es = spiExecutorServiceFactory.createExecutor(threadFactory, poolSize, poolSize);
      log.debug("getExecutorServiceForSharedWorkerPool created: " + name + " " + es + " using " + spiExecutorServiceFactory);
    } else {
      es = Executors.newFixedThreadPool(poolSize, threadFactory);
      log.debug("getExecutorServiceForSharedWorkerPool created: " + name + " " + es + "using Executors.newFixedThreadPool");
    }
    return es;
  }

  /**
   * @param options        used to source the maximum worker execution time
   * @param workerPoolSize the size of the thread pool
   * @param checker        used to monitor task completion times
   * @return either an internal or SPI provided {@link ExecutorService}
   */
  ExecutorService getExecutorServiceForDefaultWorkerPool(VertxOptions options, int workerPoolSize, BlockedThreadChecker checker) {

    VertxThreadFactory threadFactory = new VertxThreadFactory("vert.x-worker-thread-", checker, true, options.getMaxWorkerExecuteTime(),
        options.getMaxWorkerExecuteTimeUnit());
    ExecutorService es;
    if (spiExecutorServiceFactory != null) {
      es = spiExecutorServiceFactory.createExecutor(threadFactory, workerPoolSize, workerPoolSize);
      log.debug("getExecutorServiceForDefaultWorkerPool created: " + es + " using " + spiExecutorServiceFactory);
    } else {
      es = new ThreadPoolExecutor(workerPoolSize, workerPoolSize, 0L, TimeUnit.MILLISECONDS, new LinkedTransferQueue<>(), threadFactory);
      log.debug("getExecutorServiceForDefaultWorkerPool created: " + es + "using JVM's ThreadPoolExecutor");
    }
    return es;
  }
}
