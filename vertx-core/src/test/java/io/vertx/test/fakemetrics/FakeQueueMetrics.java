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

package io.vertx.test.fakemetrics;

import io.vertx.core.spi.metrics.QueueMetrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A fake implementation of the {@link QueueMetrics} SPI.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class FakeQueueMetrics implements QueueMetrics<Object> {

  private static final Object TASK_SUBMITTED = new Object();

  private final static Map<String, QueueMetrics> METRICS = new ConcurrentHashMap<>();

  private final AtomicInteger waiting = new AtomicInteger();
  private final String name;
  private final AtomicBoolean closed = new AtomicBoolean();

  public FakeQueueMetrics(String name) {
    this.name = name;
    METRICS.put(name, this);
  }

  public String getName() {
    return name;
  }

  @Override
  public synchronized Object enqueue() {
    waiting.incrementAndGet();
    return TASK_SUBMITTED;
  }

  @Override
  public void dequeue(Object queueMetric) {
    waiting.decrementAndGet();
  }

  @Override
  public void close() {
    closed.set(true);
    METRICS.remove(name);
  }

  public boolean isClosed() {
    return closed.get();
  }

  public int numberOfWaitingTasks() {
    return waiting.get();
  }

  public static Map<String, QueueMetrics> getPoolMetrics() {
    return METRICS;
  }

}
