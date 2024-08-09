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
package io.vertx.test.fakemetrics;

import io.vertx.core.spi.metrics.PoolMetrics;

import java.util.concurrent.atomic.AtomicInteger;

public class FakePoolMetrics extends FakeQueueMetrics implements PoolMetrics<Object, Object> {

  private static final Object TASK_BEGIN = new Object();

  private final int maxSize;
  private final AtomicInteger completed = new AtomicInteger();
  private final AtomicInteger submitted = new AtomicInteger();
  private final AtomicInteger idle = new AtomicInteger();
  private final AtomicInteger running = new AtomicInteger();

  public FakePoolMetrics(String name, int maxSize) {
    super(name);
    this.maxSize = maxSize;
    this.idle.set(maxSize);
  }

  public int maxSize() {
    return maxSize;
  }

  @Override
  public synchronized Object enqueue() {
    submitted.incrementAndGet();
    return super.enqueue();
  }

  @Override
  public Object begin() {
    idle.decrementAndGet();
    running.incrementAndGet();
    return TASK_BEGIN;
  }

  public void end(Object t) {
    if (t == TASK_BEGIN) {
      running.decrementAndGet();
      idle.incrementAndGet();
      completed.incrementAndGet();
    }
  }

  public int numberOfIdleThreads() {
    return idle.get();
  }

  public int numberOfRunningTasks() {
    return running.get();
  }

  public int numberOfSubmittedTask() {
    return submitted.get();
  }

  public int numberOfCompletedTasks() {
    return completed.get();
  }
}
