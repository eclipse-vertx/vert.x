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

/**
 * @author swilliams
 *
 */
public interface ThreadPoolMXBean {

  boolean isShutdown();

  boolean isTerminating();

  boolean isTerminated();

  int getCorePoolSize();

  boolean isAllowsCoreThreadTimeOut();

  int getMaximumPoolSize();

  int getQueueSize();

  int getPoolSize();

  int getActiveCount();

  int getLargestPoolSize();

  long getTaskCount();

  long getCompletedTaskCount();

}
