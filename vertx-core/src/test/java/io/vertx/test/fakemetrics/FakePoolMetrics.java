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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class FakePoolMetrics implements PoolMetrics<Object, Object> {

  private final static Map<String, FakePoolMetrics> METRICS = new ConcurrentHashMap<>();

  private static final Object TASK_SUBMITTED = new Object();
  private static final Object TASK_BEGIN = new Object();

  private final String name;
  private final int maxSize;
  private final AtomicInteger pending = new AtomicInteger();
  private final AtomicInteger releaseCount = new AtomicInteger();
  private final AtomicInteger enqueueCount = new AtomicInteger();
  private final AtomicInteger acquired = new AtomicInteger();
  private final AtomicBoolean closed = new AtomicBoolean();

  public FakePoolMetrics(String name, int maxSize) {
    this.name = name;
    this.maxSize = maxSize;
    METRICS.put(name, this);
  }

  @Override
  public synchronized Object enqueue() {
    pending.incrementAndGet();
    enqueueCount.incrementAndGet();
    return TASK_SUBMITTED;
  }

  @Override
  public void dequeue(Object queueMetric) {
    assert queueMetric == TASK_SUBMITTED;
    pending.decrementAndGet();
  }

  @Override
  public Object begin() {
    acquired.incrementAndGet();
    return TASK_BEGIN;
  }

  public void end(Object t) {
    assert t == TASK_BEGIN;
    acquired.decrementAndGet();
    releaseCount.incrementAndGet();
  }

  /**
   * @return the pool name
   */
  public String name() {
    return name;
  }

  /**
   * @return the maximum number of elements this pool can hold
   */
  public int maxSize() {
    return maxSize;
  }

  /**
   * @return the number of enqueued requests since the pool metrics was created
   */
  public int numberOfEnqueues() {
    return enqueueCount.get();
  }

  /**
   * @return the number of elements released to the pool
   */
  public int numberOfReleases() {
    return releaseCount.get();
  }

  /**
   * @return the number of requests pending in the queue
   */
  public int pending() {
    return pending.get();
  }

  /**
   * @return the number of elements currently borrowed from the pool
   */
  public int borrowed() {
    return acquired.get();
  }

  /**
   * @return the number of available elements currently in the pool
   */
  public int available() {
    return maxSize - acquired.get();
  }

  /**
   * @return whether the pool is closed
   */
  public boolean isClosed() {
    return closed.get();
  }

  @Override
  public void close() {
    closed.set(true);
    METRICS.remove(name);
  }

  public static Map<String, FakePoolMetrics> getMetrics() {
    return METRICS;
  }

  public static FakePoolMetrics getMetrics(String name) {
    return METRICS.get(name);
  }
}
