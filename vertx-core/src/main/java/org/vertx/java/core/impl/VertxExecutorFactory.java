/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.vertx.java.core.impl;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * Utility factory for creating vert.x thread pools
 * 
 * @author swilliams
 *
 */
public class VertxExecutorFactory {

  public static final long EVENT_POOL_KEEP_ALIVE = 60L;

  public static final long WORKER_POOL_KEEP_ALIVE = 60L;

  public static final int WORKER_POOL_MAX_SIZE = 20;

  public static final long ACCEPTOR_POOL_KEEP_ALIVE = 60L;

  public static ThreadPoolExecutor eventPool(String poolName) {
    int cores = Runtime.getRuntime().availableProcessors();
    int corePoolSize = Integer.getInteger("vertx.pool.event.coreSize", cores);
    int maximumPoolSize = Integer.getInteger("vertx.pool.event.maxSize", cores);
    long keepAliveTime = Long.getLong("vertx.pool.event.keepAlive", EVENT_POOL_KEEP_ALIVE);
    boolean preStart = Boolean.getBoolean("vertx.pool.event.preStart");
    TimeUnit unit = TimeUnit.SECONDS;
    BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(Integer.getInteger("vertx.pool.event.queueSize", Integer.MAX_VALUE));
    ThreadFactory threadFactory = new VertxThreadFactory(poolName);
    RejectedExecutionHandler handler = new ThreadPoolExecutor.CallerRunsPolicy();
    ThreadPoolExecutor exec = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    if (preStart) {
      exec.prestartAllCoreThreads();
    }
    return exec;
  }

  public static ThreadPoolExecutor workerPool(String poolName) {
    int corePoolSize = Integer.getInteger("vertx.pool.worker.coreSize", WORKER_POOL_MAX_SIZE);
    int maximumPoolSize = Integer.getInteger("vertx.pool.worker.maxSize", WORKER_POOL_MAX_SIZE);
    long keepAliveTime = Long.getLong("vertx.pool.worker.keepAlive", WORKER_POOL_KEEP_ALIVE);
    boolean preStart = Boolean.getBoolean("vertx.pool.event.preStart");
    TimeUnit unit = TimeUnit.SECONDS;
    BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(Integer.getInteger("vertx.pool.worker.queueSize", Integer.MAX_VALUE));
    ThreadFactory threadFactory = new VertxThreadFactory(poolName);
    RejectedExecutionHandler handler = new ThreadPoolExecutor.CallerRunsPolicy();
    ThreadPoolExecutor exec = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    if (preStart) {
      exec.prestartAllCoreThreads();
    }
    return exec;
  }

  public static ThreadPoolExecutor acceptorPool(String poolName) {
    int corePoolSize = Integer.getInteger("vertx.pool.acceptor.coreSize", 0);
    int maximumPoolSize = Integer.getInteger("vertx.pool.acceptor.maxSize", Integer.MAX_VALUE);
    long keepAliveTime = Long.getLong("vertx.pool.acceptor.keepAlive", ACCEPTOR_POOL_KEEP_ALIVE);
    boolean preStart = Boolean.getBoolean("vertx.pool.event.preStart");
    TimeUnit unit = TimeUnit.SECONDS;
    BlockingQueue<Runnable> workQueue = new SynchronousQueue<>();
    ThreadFactory threadFactory = new VertxThreadFactory(poolName);
    RejectedExecutionHandler handler = new ThreadPoolExecutor.CallerRunsPolicy();
    ThreadPoolExecutor exec = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    if (preStart) {
      exec.prestartAllCoreThreads();
    }
    return exec;
  }

}
