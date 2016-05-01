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

package io.vertx.core.impl;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.spi.NamedThreadPoolFactory;
import io.vertx.core.spi.metrics.ThreadPoolMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages named thread pool.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class NamedThreadPoolManager {

  private final Executor workerPool;
  private final Map<String, ExecutorTuple> executors = new HashMap<>();
  private final Map<String, Integer> configuration;
  private final VertxMetrics metrics;
  private final Executor orderedWorkerPool;
  private final BlockedThreadChecker checker;

  public NamedThreadPoolManager(Vertx vertx, VertxOptions options, ExecutorService workerPool, Executor
      orderedWorkerPool, VertxMetrics metrics, BlockedThreadChecker checker) {
    this.workerPool = workerPool;
    this.orderedWorkerPool = orderedWorkerPool;
    this.configuration = getNamedThreadPoolFactory(vertx, options);
    this.metrics = metrics;
    this.checker = checker;

    // Create tuples
    configuration.entrySet().forEach(entry -> {
      executors.put(entry.getKey(), new ExecutorTuple(entry.getKey(), entry.getValue()));
    });
  }

  public synchronized Executor get(String name, boolean ordered) {
    ExecutorTuple tuple = executors.get(name);
    if (tuple == null) {
      return ordered ? orderedWorkerPool : workerPool;
    }
    return ordered ? tuple.ordered : tuple.parent;
  }

  public synchronized void shutdown() {
    executors.values().forEach(tuple -> tuple.parent.shutdownNow());
  }


  private Map<String, Integer> getNamedThreadPoolFactory(Vertx vertx, VertxOptions options) {
    ServiceLoader<NamedThreadPoolFactory> services
        = ServiceLoader.load(NamedThreadPoolFactory.class);

    if (services.iterator().hasNext()) {
      NamedThreadPoolFactory factory = services.iterator().next();
      factory.configure(vertx, options.getNamedThreadPoolConfiguration());
      return new ConcurrentHashMap<>(factory.getNamedThreadPools());
    }

    return Collections.emptyMap();
  }

  public synchronized Map<String, Integer> configuration() {
    return new HashMap<>(configuration);
  }

  public ThreadPoolMetrics getMetrics(String poolName) {
    ExecutorTuple tuple = executors.get(poolName);
    return tuple != null ? tuple.poolMetrics : null;
  }

  private class ExecutorTuple {
    final ExecutorService parent;
    final Executor ordered;
    final ThreadPoolMetrics poolMetrics;

    ExecutorTuple(String name, int size) {
      parent = Executors.newFixedThreadPool(size,
          new VertxThreadFactory(name + "-", checker, true));
      ordered = new OrderedExecutorFactory(parent).getExecutor();

      if (metrics != null) {
        poolMetrics = metrics.createMetrics(name, size);
      } else {
        poolMetrics = null;
      }
    }
  }

}
