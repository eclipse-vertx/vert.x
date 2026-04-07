/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.utils;

import io.vertx.core.impl.utils.LruCache;
import org.junit.Test;

import static org.junit.Assert.*;

public class LruCacheTest {

  @Test
  public void testExpiration() {
    LruCache<String, String> cache = new LruCache<>(3);
    cache.put("0", "0");
    cache.put("1", "1");
    cache.put("2", "2");
    assertTrue(cache.containsKey("0"));
    assertTrue(cache.containsKey("1"));
    assertTrue(cache.containsKey("2"));
    assertNotNull(cache.get("0"));
    assertNotNull(cache.get("2"));
    cache.put("3", "3");
    assertTrue(cache.containsKey("0"));
    assertFalse(cache.containsKey("1"));
    assertTrue(cache.containsKey("2"));
    assertTrue(cache.containsKey("3"));
  }
}
