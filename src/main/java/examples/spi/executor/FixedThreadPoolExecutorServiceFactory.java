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
package examples.spi.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import io.vertx.core.spi.executor.ExecutorServiceFactory;

/**
 * This is a simple ExecutorServiceFactory that uses
 * {@link Executors#newFixedThreadPool}. The {@link ThreadFactory} provided by
 * Vert.x should be used to create threads. The ExecutorService can control
 * scheduling aspects such as task queueing or pool size growth within the
 * maxConcurrency parameter limit.
 */
public class FixedThreadPoolExecutorServiceFactory implements ExecutorServiceFactory {
  @Override
  public ExecutorService createExecutor(ThreadFactory threadFactory, Integer concurrency, Integer maxConcurrency) {
    return Executors.newFixedThreadPool(maxConcurrency, threadFactory);
  }
}
