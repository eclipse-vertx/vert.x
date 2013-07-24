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

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.vertx.java.core.impl.management.ManagementRegistry;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Util factory for creating vert.x thread pools
 *
 * The pools shouldn't be too configurable by the user otherwise they
 * can get into problems. Vert.x requires quite specific behaviour from each pool
 * and things can easily break if they are configured incorrectly.
 * 
 * @author swilliams
 * @author <a href="http://tfox.org">Tim Fox</a>
 *
 */
public class VertxExecutorFactory {

  public static final int WORKER_POOL_MAX_SIZE = 20;

  // The worker pool needs to be fixed with a backing queue
  public static ExecutorService workerPool(String poolName) {
    int maxSize = Integer.getInteger("vertx.pool.worker.size", WORKER_POOL_MAX_SIZE);
    ExecutorService exec = Executors.newFixedThreadPool(maxSize, new VertxThreadFactory(poolName));
    ManagementRegistry.registerThreadPool("Worker", exec);
    return exec;
  }

  // The acceptor pools need to be fixed with a backing queue

  public static EventLoopGroup eventLoopGroup(String poolName) {
    int poolSize = Integer.getInteger("vertx.pool.eventloop.size", Runtime.getRuntime().availableProcessors());
    return new NioEventLoopGroup(poolSize, new VertxThreadFactory(poolName));
  }
}
