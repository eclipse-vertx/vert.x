/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
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

  // The worker pool needs to be fixed with a backing queue
  public static ExecutorService workerPool(String poolName, int poolSize) {
    ExecutorService exec = Executors.newFixedThreadPool(poolSize, new VertxThreadFactory(poolName));
    ManagementRegistry.registerThreadPool("Worker", exec);
    return exec;
  }

  // The acceptor pools need to be fixed with a backing queue

  public static EventLoopGroup eventLoopGroup(String poolName, int poolSize) {
    return new NioEventLoopGroup(poolSize, new VertxThreadFactory(poolName));
  }

}
