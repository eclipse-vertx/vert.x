/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * A timer task that can be used as a future.
 *
 * The future is completed when the timeout expires, when the task is cancelled the future is failed
 * with a {@link java.util.concurrent.CancellationException}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface Timer extends Future<Void>, Delayed {

  /**
   * Attempt to cancel the timer task, when the timer is cancelled, the timer is
   * failed with a {@link java.util.concurrent.CancellationException}.
   *
   * @return {@code true} when the future was cancelled and the timeout didn't fire.
   */
  boolean cancel();

  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @Override
  long getDelay(TimeUnit unit);

  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @Override
  int compareTo(Delayed o);
}
