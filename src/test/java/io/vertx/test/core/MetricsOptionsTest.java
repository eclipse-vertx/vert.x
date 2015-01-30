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

package io.vertx.test.core;

import io.vertx.core.json.JsonObject;
import io.vertx.core.metrics.MetricsOptions;
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

    // Test metrics get enabled if jmx is set to true
    options.setEnabled(false);
    assertFalse(options.isJmxEnabled());
    assertEquals(options, options.setJmxEnabled(true));
    assertTrue(options.isJmxEnabled());
    assertTrue(options.isEnabled());

    assertNull(options.getJmxDomain());
    assertEquals("foo", options.setJmxDomain("foo").getJmxDomain());
  }

  @Test
  public void testCopyOptions() {
    MetricsOptions options = new MetricsOptions();

    Random rand = new Random();
    boolean metricsEnabled = rand.nextBoolean();
    boolean jmxEnabled = rand.nextBoolean();
    String jmxDomain = TestUtils.randomAlphaString(100);
    options.setEnabled(metricsEnabled);
    options.setJmxEnabled(jmxEnabled);
    options.setJmxDomain(jmxDomain);
    options = new MetricsOptions(options);
    assertEquals(metricsEnabled || jmxEnabled, options.isEnabled());
    assertEquals(jmxEnabled, options.isJmxEnabled());
    assertEquals(jmxDomain, options.getJmxDomain());
  }

  @Test
  public void testJsonOptions() {
    MetricsOptions options = new MetricsOptions(new JsonObject());
    assertFalse(options.isEnabled());
    assertFalse(options.isJmxEnabled());
    assertNull(options.getJmxDomain());
    Random rand = new Random();
    boolean metricsEnabled = rand.nextBoolean();
    boolean jmxEnabled = rand.nextBoolean();
    String jmxDomain = TestUtils.randomAlphaString(100);
    options = new MetricsOptions(new JsonObject().
        put("enabled", metricsEnabled).
        put("jmxEnabled", jmxEnabled).
        put("jmxDomain", jmxDomain)
    );
    assertEquals(metricsEnabled, options.isEnabled());
    assertEquals(jmxEnabled, options.isJmxEnabled());
    assertEquals(jmxDomain, options.getJmxDomain());
  }
}
