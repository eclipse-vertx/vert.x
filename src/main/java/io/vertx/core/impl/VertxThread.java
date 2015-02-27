/*
 * Copyright (c) 2011-2014 The original author or authors
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

import java.lang.ref.WeakReference;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class VertxThread extends Thread {

  private final boolean worker;
  private long execStart;
  // We use a weak reference so threads don't hold on to reference after context is no longer in use
  private WeakReference<ContextImpl> contextRef;

  public VertxThread(Runnable target, String name, boolean worker) {
    super(target, name);
    this.worker = worker;
  }

  ContextImpl getContext() {
    return contextRef == null ? null : contextRef.get();
  }

  void setContext(ContextImpl context) {
    this.contextRef = new WeakReference<>(context);
  }

  public void executeStart() {
    execStart = System.nanoTime();
  }

  public void executeEnd() {
    execStart = 0;
  }

  public long startTime() {
    return execStart;
  }

  public boolean isWorker() {
    return worker;
  }

}
