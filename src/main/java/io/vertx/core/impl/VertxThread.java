/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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

import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class VertxThread extends FastThreadLocalThread {

  static final String DISABLE_TCCL_PROP_NAME = "vertx.disableTCCL";
  static final boolean DISABLE_TCCL = Boolean.getBoolean(DISABLE_TCCL_PROP_NAME);

  private final boolean worker;
  private final long maxExecTime;
  private final TimeUnit maxExecTimeUnit;
  private long execStart;
  private ContextInternal context;

  public VertxThread(Runnable target, String name, boolean worker, long maxExecTime, TimeUnit maxExecTimeUnit) {
    super(target, name);
    this.worker = worker;
    this.maxExecTime = maxExecTime;
    this.maxExecTimeUnit = maxExecTimeUnit;
  }

  ContextInternal setContext(ContextInternal next) {
    ContextInternal prev = context;
    context = next;
    if (!DISABLE_TCCL) {
      setContextClassLoader(next != null ? next.classLoader() : null);
    }
    return prev;
  }

  ContextInternal getContext() {
    return context;
  }

  public final void executeStart() {
    execStart = System.nanoTime();
  }

  public final void executeEnd() {
    execStart = 0;
  }

  public long startTime() {
    return execStart;
  }

  public boolean isWorker() {
    return worker;
  }

  public long getMaxExecTime() {
    return maxExecTime;
  }

  public TimeUnit getMaxExecTimeUnit() {
    return maxExecTimeUnit;
  }
}
