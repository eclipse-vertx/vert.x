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
public final class VertxThread extends FastThreadLocalThread implements BlockedThreadChecker.Task {

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

  /**
   * @return the current context of this thread, this method must be called from the current thread
   */
  ContextInternal context() {
    return context;
  }

  private void executeStart() {
    if (context == null) {
      execStart = System.nanoTime();
    }
  }

  private void executeEnd() {
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

  /**
   * Begin the dispatch of a context task.
   * <p>
   * This is a low level interface that should not be used, instead {@link ContextInternal#dispatch(Object, io.vertx.core.Handler)}
   * shall be used.
   *
   * @param context the context on which the task is dispatched on
   * @return the current context that shall be restored
   */
  ContextInternal beginDispatch(ContextInternal context) {
    if (!ContextImpl.DISABLE_TIMINGS) {
      executeStart();
    }
    ContextInternal prev = this.context;
    this.context = context;
    return prev;
  }

  /**
   * End the dispatch of a context task.
   * <p>
   * This is a low level interface that should not be used, instead {@link ContextInternal#dispatch(Object, io.vertx.core.Handler)}
   * shall be used.
   *
   * @param prev the previous context thread to restore, might be {@code null}
   */
  void endDispatch(ContextInternal prev) {
    // We don't unset the context after execution - this is done later when the context is closed via
    // VertxThreadFactory
    context = prev;
    if (!ContextImpl.DISABLE_TIMINGS) {
      executeEnd();
    }
  }
}
