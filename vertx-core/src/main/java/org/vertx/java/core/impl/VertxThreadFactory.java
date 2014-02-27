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

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VertxThreadFactory implements ThreadFactory {

  private final String prefix;
  private final AtomicInteger threadCount = new AtomicInteger(0);

  VertxThreadFactory(String prefix) {
    this.prefix = prefix;
  }

  public Thread newThread(Runnable runnable) {
    Thread t = new VertxThread(runnable, prefix + threadCount.getAndIncrement());
    // All vert.x threads are daemons
    t.setDaemon(true);
    return t;
  }
}
