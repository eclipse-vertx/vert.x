/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 *     The Apache License v2.0 is available at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.impl;

import io.netty.util.concurrent.FastThreadLocalThread;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class VertxThread extends FastThreadLocalThread {

  private final boolean worker;
  private final long maxExecTime;
  private long execStart;
  private ContextImpl context;

  public VertxThread(Runnable target, String name, boolean worker, long maxExecTime) {
    super(target, name);
    this.worker = worker;
    this.maxExecTime = maxExecTime;
  }

  ContextImpl getContext() {
    return context;
  }

  void setContext(ContextImpl context) {
    this.context = context;
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
}
