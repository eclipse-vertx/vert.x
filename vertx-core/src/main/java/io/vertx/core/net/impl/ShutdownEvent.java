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
package io.vertx.core.net.impl;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Signals that a resource will be closed within a certain amount of time.
 */
public class ShutdownEvent {

  private final Duration timeout;

  public ShutdownEvent(long timeout, TimeUnit timeUnit) {
    this(Duration.ofMillis(timeUnit.toMillis(timeout)));
  }

  public ShutdownEvent(Duration timeout) {
    if (timeout.isNegative()) {
      throw new IllegalArgumentException("Timeout " + timeout + " must be >= 0");
    }
    this.timeout = timeout;
  }

  public Duration timeout() {
    return timeout;
  }
}
