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

package io.vertx.core.impl;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VertxThreadFactory implements ThreadFactory {

  // We store all threads in a weak map - we retain this so we can unset context from threads when
  // context is undeployed
  private static final Object FOO = new Object();
  private static Map<VertxThread, Object> weakMap = new WeakHashMap<>();

  private static synchronized void addToMap(VertxThread thread) {
    weakMap.put(thread, FOO);
  }

  private final String prefix;
  private final AtomicInteger threadCount = new AtomicInteger(0);
  private final BlockedThreadChecker checker;
  private final boolean worker;

  VertxThreadFactory(String prefix, BlockedThreadChecker checker, boolean worker) {
    this.prefix = prefix;
    this.checker = checker;
    this.worker = worker;
  }

  public static synchronized void unsetContext(ContextImpl ctx) {
    for (VertxThread thread: weakMap.keySet()) {
      if (thread.getContext() == ctx) {
        thread.setContext(null);
      }
    }
  }

  public Thread newThread(Runnable runnable) {
    VertxThread t = new VertxThread(runnable, prefix + threadCount.getAndIncrement(), worker);
    // Vert.x threads are NOT daemons - we want them to prevent JVM exit so embededd user doesn't
    // have to explicitly prevent JVM from exiting.
    if (checker != null) {
      checker.registerThread(t);
    }
    addToMap(t);
    // I know the default is false anyway, but just to be explicit-  Vert.x threads are NOT daemons
    // we want to prevent the JVM from exiting until Vert.x instances are closed
    t.setDaemon(false);
    return t;
  }
}
