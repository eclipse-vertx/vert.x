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

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.Shareable;
import io.vertx.core.shareddata.SharedData;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

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
  public void deleteElementOnComputeFunctionReturningNull() {
    LocalMap<String, String> map = sharedData.getLocalMap("foo");

    // put an initial value
    map.put("hello", "world");

    // retuning null we should remove the entry
    map.computeIfPresent("hello", (key, oldValue) -> null);
    assertFalse(map.containsKey("hello"));

    // Same for LocalMap#compute and LocalMap#compute
    map.compute("hello", (key, oldValue) -> null);
    assertFalse(map.containsKey("hello"));

    // put a value one more time
    map.put("hello", "world");
    map.merge("hello", "world!!!!!!", (key, oldValue) -> null);
    assertFalse(map.containsKey("hello"));

    map.computeIfAbsent("hello", key -> null);
    assertFalse(map.containsKey("hello"));
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
  public void testLocalMaps() {
    LocalMap<String, String> map = sharedData.getLocalMap("test-map");
    assertNotNull(map);
    assertTrue(map.isEmpty());
    assertEquals(map.size(), 0);
    assertEquals(map.entrySet().size(), 0);
    assertEquals(map.values().size(), 0);
    assertEquals(map.keySet().size(), 0);

    assertEquals(map.getOrDefault("foo", "miss"), "miss");
    assertNull(map.putIfAbsent("foo", "there"));
    assertNotNull(map.putIfAbsent("foo", "there"));
    assertEquals(map.getOrDefault("foo", "miss"), "there");
    assertEquals(map.get("foo"), "there");
    assertNull(map.get("missing"));

    assertFalse(map.isEmpty());
    assertEquals(map.size(), 1);
    assertEquals(map.entrySet().size(), 1);
    assertEquals(map.values().size(), 1);
    assertEquals(map.keySet().size(), 1);

    assertFalse(map.removeIfPresent("missing", "nope"));
    assertTrue(map.removeIfPresent("foo", "there"));

    assertNull(map.put("foo", "there"));
    assertFalse(map.replaceIfPresent("missing", "nope", "something"));
    assertTrue(map.replaceIfPresent("foo", "there", "something"));
    assertEquals(map.get("foo"), "something");

    map.compute("foo", (k, v) -> "something else");
    assertEquals(map.get("foo"), "something else");
    map.compute("bar", (k, v) -> v == null ? "was null" : "was not null");
    assertEquals(map.get("bar"), "was null");

    map.computeIfAbsent("foo", (k) -> "was not there");
    assertEquals(map.get("foo"), "something else");
    map.computeIfAbsent("baz", (k) -> "was not there");
    assertEquals(map.get("baz"), "was not there");

    assertEquals(map.remove("baz"), "was not there");

    map.computeIfPresent("foo", (k, v) -> v.equals("something else") ? "replaced" : "wrong");
    assertEquals(map.get("foo"), "replaced");

    map.computeIfAbsent("foo", k -> "was not there");
    assertEquals(map.get("foo"), "replaced");
    map.computeIfAbsent("baz", k -> "was not there");
    assertTrue(map.remove("baz", "was not there"));

    assertTrue(map.toString().contains("bar"));
    assertTrue(map.toString().contains("replaced"));

    AtomicInteger count = new AtomicInteger();
    map.forEach((k, v) -> count.incrementAndGet());
    assertEquals(count.get(), 2);

    Map<String, String> another = new HashMap<>();
    another.put("foo", "value");
    another.put("new", "another value");
    map.putAll(another);

    assertTrue(map.containsKey("new"));
    assertTrue(map.containsValue("value"));

    map.merge("new", "another value", (k, v) -> "replaced");
    assertEquals(map.get("new"), "replaced");
    map.merge("bar", "another value", (k, v) -> "inserted");
    assertEquals(map.get("bar"), "inserted");

    map.replace("bar", "replaced");
    assertEquals(map.get("bar"), "replaced");
    map.replace("bar", "replaced", "replaced 2");
    assertEquals(map.get("bar"), "replaced 2");
    map.replace("bar", "replaced", "replaced 2");
    assertEquals(map.get("bar"), "replaced 2");

    map.replaceAll((k, v) -> {
      if (k.equals("new")) {
        return "new";
      }
      return v;
    });
    assertEquals(map.get("new"), "new");

    map.clear();
    assertTrue(map.isEmpty());

    map.close();
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

  @Test
  public void testCopyOnGet() {
    testMapOperationResult(LocalMap::get);
  }

  @Test
  public void testCopyOnPutIfAbsent() {
    testMapOperationResult((map, key) -> map.putIfAbsent(key, new ShareableObject("some other test data")));
  }

  @Test
  public void testCopyOnRemove() {
    testMapOperationResult(LocalMap::remove);
  }

  private void testMapOperationResult(BiFunction<LocalMap<String, ShareableObject>, String, ShareableObject> operation) {
    final String key = "key";
    final ShareableObject value = new ShareableObject("some test data");
    final LocalMap<String, ShareableObject> map = vertx.sharedData().getLocalMap("foo");
    assertNull(map.put(key, value));

    final ShareableObject result = operation.apply(map, key);

    assertEquals(value, result);
    assertNotSame(value, result);
  }

  private static class ShareableObject implements Shareable {
    private final String data;

    public ShareableObject(String data) {
      this.data = data;
    }

    @Override
    public Shareable copy() {
      return new ShareableObject(data);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final ShareableObject another = (ShareableObject) o;
      return data.equals(another.data);
    }

    @Override
    public int hashCode() {
      return data.hashCode();
    }
  }

  class SomeOtherClass {
  }

}
