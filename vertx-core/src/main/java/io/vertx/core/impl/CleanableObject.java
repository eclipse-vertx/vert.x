/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.impl;

import java.lang.ref.Cleaner;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Base object for cleanable proxies.
 */
public class CleanableObject {

  public static final Duration DEFAULT_CLEAN_TIMEOUT = Duration.ofSeconds(30);

  private static class Action implements Runnable {

    private Consumer<Duration> dispose;
    private Duration timeout = DEFAULT_CLEAN_TIMEOUT;

    public Action(Consumer<Duration> dispose) {
      this.dispose = dispose;
    }

    @Override
    public void run() {
      Consumer<Duration> d = dispose;
      dispose = null;
      d.accept(timeout);
    }
  }

  private Cleaner.Cleanable cleanable;
  private Action action;

  public CleanableObject(Cleaner cleaner, Consumer<Duration> dispose) {
    this.action = new Action(dispose);
    this.cleanable = cleaner.register(this, action);
  }

  protected final void clean(Duration timeout) {
    if (timeout.isNegative()) {
      throw new IllegalArgumentException();
    }
    Action action;
    Cleaner.Cleanable cleanable;
    synchronized (this) {
      action = this.action;
      cleanable = this.cleanable;
      this.action = null;
      this.cleanable = null;
    }
    if (action != null) {
      assert cleanable != null;
      action.timeout = timeout;
      cleanable.clean();
    }
  }
}
