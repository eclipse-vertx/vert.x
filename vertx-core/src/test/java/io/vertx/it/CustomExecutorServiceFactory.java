/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.it;

import io.vertx.core.spi.ExecutorServiceFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomExecutorServiceFactory implements ExecutorServiceFactory {

  public static final AtomicInteger NUM = new AtomicInteger();

  @Override
  public ExecutorService createExecutor(ThreadFactory threadFactory, Integer concurrency, Integer maxConcurrency) {
    NUM.incrementAndGet();
    return new CustomExecutorService(threadFactory, concurrency, maxConcurrency);
  }
}
