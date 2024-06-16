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

package io.vertx.test.fakemetrics;

import io.vertx.core.metrics.Measured;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.spi.metrics.MetricsProvider;
import junit.framework.AssertionFailedError;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FakeMetricsBase implements Metrics {

  private static volatile Throwable unexpectedError;

  private boolean closed;

  public static <M extends FakeMetricsBase> M getMetrics(Measured measured) {
    return (M) ((MetricsProvider) measured).getMetrics();
  }

  public FakeMetricsBase() {
  }

  public static void registerFailure(Throwable failure) {
    unexpectedError = failure;
  }

  public static void sanityCheck() {
    Throwable err = unexpectedError;
    if (err != null) {
      AssertionFailedError afe = new AssertionFailedError();
      afe.initCause(err);
      unexpectedError = null;
      throw afe;
    }
  }

  @Override
  public synchronized void close() {
    if (closed) {
      registerFailure(new IllegalStateException(getClass().getSimpleName() + " already closed"));
    }
    closed = true;
  }
}
