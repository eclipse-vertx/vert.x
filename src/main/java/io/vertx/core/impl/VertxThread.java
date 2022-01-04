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
import io.vertx.core.impl.btc.BlockedThreadChecker;

import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class VertxThread extends FastThreadLocalThread implements BlockedThreadChecker.Task {

  private final boolean worker;
  private final long maxExecTime;
  private final TimeUnit maxExecTimeUnit;
  private long execStart;
  ContextInternal context;
  ClassLoader topLevelTCCL;

  public VertxThread(Runnable target, String name, boolean worker, long maxExecTime, TimeUnit maxExecTimeUnit) {
    super(target, name);
    this.worker = worker;
    this.maxExecTime = maxExecTime;
    this.maxExecTimeUnit = maxExecTimeUnit;
  }

  /**
   * @return the current context of this thread, this method must be called from the current thread
   */
  ContextInternal context() {
    return context;
  }

  void executeStart() {
    if (context == null) {
      execStart = System.nanoTime();
    }
  }

  void executeEnd() {
    if (context == null) {
      execStart = 0;
    }
  }

  public long startTime() {
    return execStart;
  }

  public boolean isWorker() {
    return worker;
  }

  @Override
  public long maxExecTime() {
    return maxExecTime;
  }

  @Override
  public TimeUnit maxExecTimeUnit() {
    return maxExecTimeUnit;
  }

}
