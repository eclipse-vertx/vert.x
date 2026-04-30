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
package io.vertx.test.core;

import io.vertx.core.Vertx;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A factory for Vert.x instance.
 */
public interface VertxProvider extends Callable<Vertx> {

  default void close(Vertx vertx, Duration timeout) throws Exception {
    vertx
      .close()
      .await(timeout.toMillis(), TimeUnit.MILLISECONDS);
  }
}
