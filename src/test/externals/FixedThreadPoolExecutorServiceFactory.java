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

/**
 * This source file is not in the usual Vert.x IDE project's classpath but instead
 * compiled (and the .class file place on the classpath) by some tests. This is
 * to more closely emulate typical use of the {@link ExecutorServiceFactory} SPI
 * interface and to allow for testing with different SPI implementations on the
 * classpath.
 */
package io.vertx.core.externals;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import io.vertx.core.spi.executor.ExecutorServiceFactory;

/**
 * This is a simple test ExecutorServiceFactory that overrides the base executor
 * service to use {@link Executors#newFixedThreadPool}
 */
public class FixedThreadPoolExecutorServiceFactory extends ExternalLoadableExecutorServiceFactory {
  @Override
  ExecutorService getBaseExecutorService(ThreadFactory threadFactory, Integer maxConcurrency) {
    return Executors.newFixedThreadPool(maxConcurrency, threadFactory);
  }
}
