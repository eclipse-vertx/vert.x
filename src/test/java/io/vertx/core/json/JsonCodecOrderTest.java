/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.json;


import io.vertx.core.spi.json.JsonCodec;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.TreeMap;

import static org.junit.Assert.assertSame;

public class JsonCodecOrderTest {

  static {
    // Make sure that the default Jackson codec is initialized before running this test to not mess other tests
    Object codec = JsonCodec.INSTANCE;
  }

  @Test
  public void loadFactoriesFromTCCL() throws Exception {
    ClassLoader custom = new URLClassLoader(new URL[]{new File("target/classpath/jsoncodecorder").toURI().toURL()});

    // Try with the TCCL classloader
    final ClassLoader originalTCCL = Thread.currentThread().getContextClassLoader();
    JsonCodec factory;
    try {
      Thread.currentThread().setContextClassLoader(custom);
      factory = JsonCodec.loadCodec();
    } finally {
      Thread.currentThread().setContextClassLoader(originalTCCL);
    }

    // Expected
    TreeMap<Integer, Class<? extends JsonCodec>> tree = new TreeMap<>();
    tree.put(JsonCodec1.ORDER, JsonCodec1.class);
    tree.put(JsonCodec2.ORDER, JsonCodec2.class);
    tree.put(JsonCodec3.ORDER, JsonCodec3.class);

    assertSame(tree.values().iterator().next(), factory.getClass());
  }
}
