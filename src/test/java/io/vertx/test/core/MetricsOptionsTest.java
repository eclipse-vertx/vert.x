/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.metrics.impl.DummyVertxMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.test.fakemetrics.FakeVertxMetrics;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Enumeration;
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
    VertxMetrics metrics = ((VertxInternal) vertx).metricsSPI();
    assertNull(metrics);
  }

  @Test
  public void testSetMetricsInstance() {
    DummyVertxMetrics metrics = DummyVertxMetrics.INSTANCE;
    vertx.close();
    vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true).setFactory(new SimpleVertxMetricsFactory<>(metrics))));
    assertSame(metrics, ((VertxInternal) vertx).metricsSPI());
  }

  @Test
  public void testMetricsFromServiceLoader() {
    vertx.close();
    VertxOptions options = new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true));
    vertx = createVertxLoadingMetricsFromMetaInf(options, "io.vertx.test.fakemetrics.FakeMetricsFactory");
    VertxMetrics metrics = ((VertxInternal) vertx).metricsSPI();
    assertNotNull(metrics);
    assertTrue(metrics instanceof FakeVertxMetrics);
  }

  @Test
  public void testSetMetricsInstanceTakesPrecedenceOverServiceLoader() {
    DummyVertxMetrics metrics = DummyVertxMetrics.INSTANCE;
    vertx.close();
    VertxOptions options = new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true).setFactory(new SimpleVertxMetricsFactory<>(metrics)));
    vertx = createVertxLoadingMetricsFromMetaInf(options, "io.vertx.test.fakemetrics.FakeMetricsFactory");
    assertSame(metrics, ((VertxInternal) vertx).metricsSPI());
  }

  static Vertx createVertxLoadingMetricsFromMetaInf(VertxOptions options, String factoryFqn) {
    ClassLoader oldCL = Thread.currentThread().getContextClassLoader();
    ClassLoader cl = createMetricsFromMetaInfLoader(factoryFqn);
    Thread.currentThread().setContextClassLoader(cl);
    try {
      return Vertx.vertx(options);
    } finally {
      Thread.currentThread().setContextClassLoader(oldCL);
    }
  }

  static ClassLoader createMetricsFromMetaInfLoader(String factoryFqn) {
    return new URLClassLoader(new URL[0], Thread.currentThread().getContextClassLoader()) {
      @Override
      public Enumeration<URL> findResources(String name) throws IOException {
        if (name.equals("META-INF/services/io.vertx.core.spi.VertxMetricsFactory")) {
          File f = File.createTempFile("vertx", ".txt");
          f.deleteOnExit();
          Files.write(f.toPath(), factoryFqn.getBytes());
          return Collections.enumeration(Collections.singleton(f.toURI().toURL()));
        }
        return super.findResources(name);
      }
    };
  }
}
