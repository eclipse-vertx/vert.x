/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.it.metrics;

import io.vertx.core.Vertx;
import io.vertx.core.VertxBuilder;
import io.vertx.core.VertxOptions;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.test.fakemetrics.FakeVertxMetrics;
import org.junit.Test;

import static org.junit.Assert.*;

public class ServiceLoaderTest {

  @Test
  public void testMetricsFromServiceLoader() {
    testMetricsFromServiceLoader(true);
  }

  @Test
  public void testMetricsFromServiceLoaderDisabled() {
    testMetricsFromServiceLoader(false);
  }

  private void testMetricsFromServiceLoader(boolean enabled) {
    MetricsOptions metricsOptions = new MetricsOptions().setEnabled(enabled);
    VertxOptions options = new VertxOptions().setMetricsOptions(metricsOptions);
    Vertx vertx = Vertx.vertx(options);
    VertxMetrics metrics = ((VertxInternal) vertx).metrics();
    if (enabled) {
      assertNotNull(metrics);
      assertTrue(metrics instanceof FakeVertxMetrics);
      assertEquals(metricsOptions.isEnabled(), ((FakeVertxMetrics)metrics).options().isEnabled());
    } else {
      assertNull(metrics);
    }
  }

  @Test
  public void testSetMetricsInstanceTakesPrecedenceOverServiceLoader() {
    VertxMetrics metrics = new VertxMetrics() {
    };
    VertxBuilder builder = Vertx.builder()
      .with(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)))
      .withMetrics(options -> metrics);
    Vertx vertx = builder.build();
    assertSame(metrics, ((VertxInternal) vertx).metrics());
  }
}
