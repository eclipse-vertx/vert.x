/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.vertx.java.core.impl.management;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author swilliams
 *
 */
public class ThreadPoolMXBeanImpl implements ThreadPoolMXBean {

  private ThreadPoolExecutor executor;

  /**
   * @param exec
   */
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
