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

package io.vertx.it.json;

import io.vertx.core.json.Json;
import io.vertx.core.spi.JsonFactory;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.TreeMap;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class JsonFactoryOrderingTest extends VertxTestBase {

  static {
    // Make sure that the default Jackson codec is initialized before running this test
    Object codec = Json.CODEC;
  }

  @Test
  public void loadFactoriesFromTCCL() throws Exception {
    ClassLoader custom = new URLClassLoader(new URL[]{new File("target/classpath/jsonfactory").toURI().toURL()});

    // Try with the TCCL classloader
    final ClassLoader originalTCCL = Thread.currentThread().getContextClassLoader();
    JsonFactory factory;
    try {
      Thread.currentThread().setContextClassLoader(custom);
      factory = JsonFactory.load();
    } finally {
      Thread.currentThread().setContextClassLoader(originalTCCL);
    }

    // Expected
    TreeMap<Integer, Class<? extends JsonFactory>> tree = new TreeMap<>();
    tree.put(JsonFactory1.ORDER, JsonFactory1.class);
    tree.put(JsonFactory2.ORDER, JsonFactory2.class);
    tree.put(JsonFactory3.ORDER, JsonFactory3.class);

    assertSame(tree.values().iterator().next(), factory.getClass());
  }
}
