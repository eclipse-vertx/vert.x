/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 and the Apache License, Version 2.0
 * which accompanies this distribution. The Eclipse Public License 2.0 is
 * available at http://www.eclipse.org/legal/epl-2.0.html, and the Apache
 * License, Version 2.0 is available at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
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
