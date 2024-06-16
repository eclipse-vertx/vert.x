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

import io.netty.util.concurrent.FastThreadLocalThread;
import io.vertx.core.internal.threadchecker.ThreadInfo;
import io.vertx.core.internal.ContextInternal;

import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class VertxThread extends FastThreadLocalThread {

  private final boolean worker;
  final ThreadInfo info;
  ContextInternal context;
  ClassLoader topLevelTCCL;

  public VertxThread(Runnable target, String name, boolean worker, long maxExecTime, TimeUnit maxExecTimeUnit) {
    super(target, name);
    this.worker = worker;
    this.info = new ThreadInfo(maxExecTimeUnit, maxExecTime);
  }

  /**
   * @return the current context of this thread, this method must be called from the current thread
   */
  ContextInternal context() {
    return context;
  }

  void executeStart() {
    if (context == null) {
      info.startTime = System.nanoTime();
    }
  }

  void executeEnd() {
    if (context == null) {
      info.startTime = 0;
    }
  }

  public long startTime() {
    return info.startTime;
  }

  public boolean isWorker() {
    return worker;
  }

  public long maxExecTime() {
    return info.maxExecTime;
  }

  public TimeUnit maxExecTimeUnit() {
    return info.maxExecTimeUnit;
  }

}
