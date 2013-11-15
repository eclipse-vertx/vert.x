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
package org.vertx.java.core.impl.management;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author swilliams
 *
 */
public class ThreadPoolMXBeanImpl implements ThreadPoolMXBean {

  private final ThreadPoolExecutor executor;

  public ThreadPoolMXBeanImpl(ThreadPoolExecutor executor) {
    this.executor = executor;
  }

  /* (non-Javadoc)
   * @see org.vertx.java.core.impl.management.ThreadPoolMXBean#isShutdown()
   */
  @Override
  public boolean isShutdown() {
    return executor.isShutdown();
  }

  /* (non-Javadoc)
   * @see org.vertx.java.core.impl.management.ThreadPoolMXBean#isTerminating()
   */
  @Override
  public boolean isTerminating() {
    return executor.isTerminating();
  }

  /* (non-Javadoc)
   * @see org.vertx.java.core.impl.management.ThreadPoolMXBean#isTerminated()
   */
  @Override
  public boolean isTerminated() {
    return executor.isTerminated();
  }

  /* (non-Javadoc)
   * @see org.vertx.java.core.impl.management.ThreadPoolMXBean#getCorePoolSize()
   */
  @Override
  public int getCorePoolSize() {
    return executor.getCorePoolSize();
  }

  /* (non-Javadoc)
   * @see org.vertx.java.core.impl.management.ThreadPoolMXBean#isAllowsCoreThreadTimeOut()
   */
  @Override
  public boolean isAllowsCoreThreadTimeOut() {
    return executor.allowsCoreThreadTimeOut();
  }

  /* (non-Javadoc)
   * @see org.vertx.java.core.impl.management.ThreadPoolMXBean#getMaximumPoolSize()
   */
  @Override
  public int getMaximumPoolSize() {
    return executor.getMaximumPoolSize();
  }

  /* (non-Javadoc)
   * @see org.vertx.java.core.impl.management.ThreadPoolMXBean#getQueue()
   */
  @Override
  public int getQueueSize() {
    return executor.getQueue().size();
  }

  /* (non-Javadoc)
   * @see org.vertx.java.core.impl.management.ThreadPoolMXBean#getPoolSize()
   */
  @Override
  public int getPoolSize() {
    return executor.getPoolSize();
  }

  /* (non-Javadoc)
   * @see org.vertx.java.core.impl.management.ThreadPoolMXBean#getActiveCount()
   */
  @Override
  public int getActiveCount() {
    return executor.getActiveCount();
  }

  /* (non-Javadoc)
   * @see org.vertx.java.core.impl.management.ThreadPoolMXBean#getLargestPoolSize()
   */
  @Override
  public int getLargestPoolSize() {
    return executor.getLargestPoolSize();
  }

  /* (non-Javadoc)
   * @see org.vertx.java.core.impl.management.ThreadPoolMXBean#getTaskCount()
   */
  @Override
  public long getTaskCount() {
    return executor.getTaskCount();
  }

  /* (non-Javadoc)
   * @see org.vertx.java.core.impl.management.ThreadPoolMXBean#getCompletedTaskCount()
   */
  @Override
  public long getCompletedTaskCount() {
    return executor.getCompletedTaskCount();
  }
}
