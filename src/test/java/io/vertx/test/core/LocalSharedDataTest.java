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
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.SharedData;
import org.junit.Test;

import java.util.Collection;
import java.util.Random;
import java.util.Set;

import static io.vertx.test.core.TestUtils.*;

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

    JsonObject obj = new JsonObject().put("foo", "bar");
    map.put("obj", obj);
    JsonObject other = (JsonObject)map.get("obj");
    assertEquals(obj, other);
    assertNotSame(obj, other); // Should be copied

    JsonArray arr = new JsonArray().add("foo");
    map.put("arr", arr);
    JsonArray otherArr = (JsonArray)map.get("arr");
    assertEquals(arr, otherArr);
    assertNotSame(arr, otherArr); // Should be copied
  }

  @Test
  public void testKeys() {
    LocalMap<String, String> map = sharedData.getLocalMap("foo");
    map.put("foo1", "val1");
    map.put("foo2", "val2");
    map.put("foo3", "val3");
    assertEquals(3, map.size());
    Set<String> keys = map.keySet();
    assertEquals(3, keys.size());
    assertTrue(keys.contains("foo1"));
    assertTrue(keys.contains("foo2"));
    assertTrue(keys.contains("foo3"));
  }

  @Test
  public void testKeysCopied() {
    LocalMap<JsonObject, String> map = sharedData.getLocalMap("foo");
    JsonObject json1 = new JsonObject().put("foo1", "val1");
    JsonObject json2 = new JsonObject().put("foo2", "val1");
    JsonObject json3 = new JsonObject().put("foo3", "val1");

    map.put(json1, "val1");
    map.put(json2, "val2");
    map.put(json3, "val3");

    assertEquals(3, map.size());
    Set<JsonObject> keys = map.keySet();
    assertEquals(3, keys.size());
    assertTrue(keys.contains(json1));
    assertTrue(keys.contains(json2));
    assertTrue(keys.contains(json3));

    // copied
    assertFalse(containsExact(keys, json1));
    assertFalse(containsExact(keys, json2));
    assertFalse(containsExact(keys, json3));
  }

  private boolean containsExact(Collection<JsonObject> coll, JsonObject obj) {
    for (JsonObject j: coll) {
      if (j == obj) {
        return true;
      }
    }
    return false;
  }

  @Test
  public void testValues() {
    LocalMap<String, String> map = sharedData.getLocalMap("foo");
    map.put("foo1", "val1");
    map.put("foo2", "val2");
    map.put("foo3", "val3");
    assertEquals(3, map.size());
    Collection<String> values = map.values();
    assertEquals(3, values.size());
    assertTrue(values.contains("val1"));
    assertTrue(values.contains("val2"));
    assertTrue(values.contains("val3"));
  }

  @Test
  public void testValuesCopied() {
    LocalMap<String, JsonObject> map = sharedData.getLocalMap("foo");
    JsonObject json1 = new JsonObject().put("foo1", "val1");
    JsonObject json2 = new JsonObject().put("foo2", "val1");
    JsonObject json3 = new JsonObject().put("foo3", "val1");

    map.put("key1", json1);
    map.put("key2", json2);
    map.put("key3", json3);

    assertEquals(3, map.size());
    Collection<JsonObject> values = map.values();
    assertEquals(3, values.size());
    assertTrue(values.contains(json1));
    assertTrue(values.contains(json2));
    assertTrue(values.contains(json3));

    // copied
    assertFalse(containsExact(values, json1));
    assertFalse(containsExact(values, json2));
    assertFalse(containsExact(values, json3));
  }



  class SomeOtherClass {
  }

}
