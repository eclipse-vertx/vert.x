/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
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

  private static final ConcurrentHashMap<Object, FakeMetricsBase> metricsMap = new ConcurrentHashMap<>();

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
