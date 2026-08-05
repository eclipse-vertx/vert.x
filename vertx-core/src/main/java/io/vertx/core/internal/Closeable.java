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
package io.vertx.core.internal;

import io.vertx.core.Future;

import java.time.Duration;

/**
 * Closeable contract, it will eventually replace {@link io.vertx.core.Closeable}.
 */
@FunctionalInterface
public interface Closeable {

  Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

  /**
   * Close immediately ({@code shutdown(0, TimeUnit.SECONDS}).
   *
   * @return a future notified when the client is closed
   */
  default Future<Void> close() {
    return shutdown(Duration.ZERO);
  }

  /**
   * Shutdown with a 30 seconds timeout ({@code shutdown(30, TimeUnit.SECONDS)}).
   *
   * @return a future completed when shutdown has completed
   */
  default Future<Void> shutdown() {
    return shutdown(DEFAULT_TIMEOUT);
  }

  /**
   * Shutdown.
   *
   * @param timeout the amount of time after which all resources are forcibly closed
   * @return a future notified when the client is closed
   */
  Future<Void> shutdown(Duration timeout);

}
