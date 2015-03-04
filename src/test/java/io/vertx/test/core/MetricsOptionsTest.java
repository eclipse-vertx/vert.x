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
    options = new MetricsOptions(new JsonObject().
        put("enabled", metricsEnabled)
    );
    assertEquals(metricsEnabled, options.isEnabled());
  }
}
