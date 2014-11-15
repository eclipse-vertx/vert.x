/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.SharedData;
import org.junit.Test;

import java.util.Random;

import static io.vertx.test.core.TestUtils.assertIllegalArgumentException;
import static io.vertx.test.core.TestUtils.assertNullPointerException;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class LocalSharedDataTest extends VertxTestBase {

  private SharedData sharedData;

  public void setUp() throws Exception {
    super.setUp();
    sharedData = vertx.sharedData();
  }

  @Test
  public void testMap() throws Exception {
    assertNullPointerException(() -> sharedData.getLocalMap(null));
    LocalMap<String, String> map = sharedData.getLocalMap("foo");
    LocalMap<String, String> map2 = sharedData.getLocalMap("foo");
    assertTrue(map == map2);
    LocalMap<String, String> map3 = sharedData.getLocalMap("bar");
    assertFalse(map3 == map2);
    map.close();
    LocalMap<String, String> map4 = sharedData.getLocalMap("foo");
    assertFalse(map4 == map3);
  }

  @Test
  public void testMapTypes() throws Exception {

    LocalMap map = sharedData.getLocalMap("foo");

    String key = "key";

    double d = new Random().nextDouble();
    map.put(key, d);
    assertEquals(d, map.get(key));

    float f = new Random().nextFloat();
    map.put(key, f);
    assertEquals(f, map.get(key));

    byte b = (byte)new Random().nextInt();
    map.put(key, b);
    assertEquals(b, map.get(key));

    short s = (short)new Random().nextInt();
    map.put(key, s);
    assertEquals(s, map.get(key));

    int i = new Random().nextInt();
    map.put(key, i);
    assertEquals(i, map.get(key));

    long l = new Random().nextLong();
    map.put(key, l);
    assertEquals(l, map.get(key));

    map.put(key, true);
    assertTrue((Boolean)map.get(key));

    map.put(key, false);
    assertFalse((Boolean) map.get(key));

    char c = (char)new Random().nextLong();
    map.put(key, c);
    assertEquals(c, map.get(key));

    Buffer buff = TestUtils.randomBuffer(100);
    map.put(key, buff);
    Buffer got1 = (Buffer)map.get(key);
    assertTrue(got1 != buff); // Make sure it's copied
    assertEquals(buff, map.get(key));
    Buffer got2 = (Buffer)map.get(key);
    assertTrue(got1 != got2); // Should be copied each time
    assertTrue(got2 != buff);
    assertEquals(buff, map.get(key));


    byte[] bytes = TestUtils.randomByteArray(100);
    map.put(key, bytes);
    byte[] bgot1 = (byte[]) map.get(key);
    assertTrue(bgot1 != bytes);
    assertTrue(TestUtils.byteArraysEqual(bytes, bgot1));
    byte[] bgot2 = (byte[]) map.get(key);
    assertTrue(bgot2 != bytes);
    assertTrue(bgot1 != bgot2);
    assertTrue(TestUtils.byteArraysEqual(bytes, bgot2));

    assertIllegalArgumentException(() -> map.put(key, new SomeOtherClass()));
  }



  class SomeOtherClass {
  }

}
