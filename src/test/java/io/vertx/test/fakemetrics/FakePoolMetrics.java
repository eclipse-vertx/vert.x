/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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

/**
 * A fake implementation of the {@link PoolMetrics} SPI.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class FakePoolMetrics implements PoolMetrics<Void> {
  private final static Map<String, PoolMetrics> METRICS = new ConcurrentHashMap<>();

  private final int poolSize;

  private final AtomicInteger submitted = new AtomicInteger();
  private final AtomicInteger completed = new AtomicInteger();
  private final AtomicInteger idle = new AtomicInteger();
  private final AtomicInteger waiting = new AtomicInteger();
  private final AtomicInteger running = new AtomicInteger();
  private final String name;
  private final AtomicBoolean closed = new AtomicBoolean();

  public FakePoolMetrics(String name, int poolSize) {
    this.poolSize = poolSize;
    this.name = name;
    this.idle.set(this.poolSize);
    METRICS.put(name, this);
  }

  public int getPoolSize() {
    return poolSize;
  }

  public String getName() {
    return name;
  }

  public synchronized Void submitted() {
    submitted.incrementAndGet();
    waiting.incrementAndGet();
    return null;
  }

  @Override
  public void rejected(Void t) {
    waiting.decrementAndGet();
  }

  @Override
  public Void begin(Void t) {
    waiting.decrementAndGet();
    idle.decrementAndGet();
    running.incrementAndGet();
    return null;
  }

  public void end(Void t, boolean succeeded) {
    running.decrementAndGet();
    idle.incrementAndGet();
    completed.incrementAndGet();
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public void close() {
    closed.set(true);
    METRICS.remove(name);
  }

  public boolean isClosed() {
    return closed.get();
  }

  public int numberOfSubmittedTask() {
    return submitted.get();
  }

  public int numberOfCompletedTasks() {
    return completed.get();
  }

  public int numberOfWaitingTasks() {
    return waiting.get();
  }

  public int numberOfIdleThreads() {
    return idle.get();
  }

  public int numberOfRunningTasks() {
    return running.get();
  }

  public static Map<String, PoolMetrics> getPoolMetrics() {
    return METRICS;
  }

}
