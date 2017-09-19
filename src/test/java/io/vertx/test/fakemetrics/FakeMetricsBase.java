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

package io.vertx.test.fakemetrics;

import io.vertx.core.Vertx;
import io.vertx.core.metrics.Measured;
import io.vertx.core.spi.metrics.Metrics;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FakeMetricsBase implements Metrics {

  public static final ConcurrentHashMap<Object, FakeMetricsBase> metricsMap = new ConcurrentHashMap<>();

  public static <M extends FakeMetricsBase> M getMetrics(Measured measured) {
    // Free cast :-)
    return (M) metricsMap.get(measured);
  }

  final Measured key;

  public FakeMetricsBase(Measured measured) {
    metricsMap.put(measured, this);
    key = measured;
  }

  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public void close() {
    metricsMap.remove(key);
  }
}
