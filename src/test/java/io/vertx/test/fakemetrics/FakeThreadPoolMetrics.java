/*
 *  Copyright (c) 2011-2015 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *       The Eclipse Public License is available at
 *       http://www.eclipse.org/legal/epl-v10.html
 *
 *       The Apache License v2.0 is available at
 *       http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
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
public class FakeThreadPoolMetrics implements PoolMetrics<Void> {
  private final static Map<String, PoolMetrics> METRICS = new ConcurrentHashMap<>();

  private final int poolSize;

  private final AtomicInteger submitted = new AtomicInteger();
  private final AtomicInteger completed = new AtomicInteger();
  private final AtomicInteger idle = new AtomicInteger();
  private final AtomicInteger waiting = new AtomicInteger();
  private final AtomicInteger running = new AtomicInteger();
  private final String name;
  private final AtomicBoolean closed = new AtomicBoolean();

  public FakeThreadPoolMetrics(String name, int poolSize) {
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

  @Override
  public synchronized Void taskSubmitted() {
    submitted.incrementAndGet();
    waiting.incrementAndGet();
    return null;
  }

  @Override
  public void taskRejected(Void task) {
    waiting.decrementAndGet();
  }

  @Override
  public void taskBegin(Void task) {
    waiting.decrementAndGet();
    idle.decrementAndGet();
    running.incrementAndGet();
  }

  @Override
  public void taskEnd(Void task, boolean succeeded) {
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

  public int submitted() {
    return submitted.get();
  }

  public int completed() {
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

  public static Map<String, PoolMetrics> getThreadPoolMetrics() {
    return METRICS;
  }

}