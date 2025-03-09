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

package io.vertx.tests.metrics;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.Random;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class MetricsOptionsTest extends VertxTestBase {

  @Test
  public void testOptions() {
    MetricsOptions options = new MetricsOptions();

    assertFalse(options.isEnabled());
    assertEquals(options, options.setEnabled(true));
    assertTrue(options.isEnabled());
  }

  @Test
  public void testCopyOptions() {
    MetricsOptions options = new MetricsOptions();

    Random rand = new Random();
    boolean metricsEnabled = rand.nextBoolean();
    options.setEnabled(metricsEnabled);
    options = new MetricsOptions(options);
    assertEquals(metricsEnabled, options.isEnabled());
  }

  @Test
  public void testJsonOptions() {
    MetricsOptions options = new MetricsOptions(new JsonObject());
    assertFalse(options.isEnabled());
    Random rand = new Random();
    boolean metricsEnabled = rand.nextBoolean();
    String customValue = TestUtils.randomAlphaString(10);
    options = new MetricsOptions(new JsonObject().
        put("enabled", metricsEnabled).
        put("custom", customValue)
    );
    assertEquals(metricsEnabled, options.isEnabled());
    assertEquals(metricsEnabled, options.toJson().getBoolean("enabled"));
    assertEquals(customValue, options.toJson().getString("custom"));
  }

  @Test
  public void testMetricsEnabledWithoutConfig() {
    vertx.close();
    vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)));
    VertxMetrics metrics = ((VertxInternal) vertx).metrics();
    assertNull(metrics);
  }

  @Test
  public void testSetMetricsInstance() {
    VertxMetrics metrics = new VertxMetrics() {
    };
    vertx.close();
    vertx = Vertx
      .builder()
      .with(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)))
      .withMetrics(new SimpleVertxMetricsFactory<>(metrics))
      .build();
    assertSame(metrics, ((VertxInternal) vertx).metrics());
  }
}
