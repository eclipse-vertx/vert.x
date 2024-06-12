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

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static io.vertx.it.CustomExecutorServiceFactory.NUM;

public class CustomExecutorService extends ThreadPoolExecutor {

  public CustomExecutorService(ThreadFactory threadFactory, int corePoolSize, int maximumPoolSize) {
    super(corePoolSize, maximumPoolSize, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), threadFactory);
  }

  public static final ThreadLocal<Boolean> executing = ThreadLocal.withInitial(() -> false);

  @Override
  protected void beforeExecute(Thread t, Runnable r) {
    executing.set(true);
  }

  @Override
  protected void afterExecute(Runnable r, Throwable t) {
    executing.set(false);
  }

  @Override
  protected void terminated() {
    NUM.decrementAndGet();
  }
}
