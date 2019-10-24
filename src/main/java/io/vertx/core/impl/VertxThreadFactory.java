/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VertxThreadFactory implements ThreadFactory {

  private final String prefix;
  private final AtomicInteger threadCount = new AtomicInteger(0);
  private final BlockedThreadChecker checker;
  private final boolean worker;
  private final long maxExecTime;
  private final TimeUnit maxExecTimeUnit;

  VertxThreadFactory(String prefix, BlockedThreadChecker checker, boolean worker, long maxExecTime, TimeUnit maxExecTimeUnit) {
    this.prefix = prefix;
    this.checker = checker;
    this.worker = worker;
    this.maxExecTime = maxExecTime;
    this.maxExecTimeUnit = maxExecTimeUnit;
  }

  public Thread newThread(Runnable runnable) {
    VertxThread t = new VertxThread(runnable, prefix + threadCount.getAndIncrement(), worker, maxExecTime, maxExecTimeUnit);
    // Vert.x threads are NOT daemons - we want them to prevent JVM exit so embededd user doesn't
    // have to explicitly prevent JVM from exiting.
    if (checker != null) {
      checker.registerThread(t, t);
    }
    // I know the default is false anyway, but just to be explicit-  Vert.x threads are NOT daemons
    // we want to prevent the JVM from exiting until Vert.x instances are closed
    t.setDaemon(false);
    return t;
  }
}
