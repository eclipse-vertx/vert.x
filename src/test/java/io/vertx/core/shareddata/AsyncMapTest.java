/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.shareddata;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.io.Serializable;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static io.vertx.test.core.TestUtils.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class AsyncMapTest extends VertxTestBase {

  protected abstract Vertx getVertx();

  @Test
  public void testMapPutGetByte() {
    testMapPutGet((byte)1, (byte)2);
  }

  @Test
  public void testMapPutGetShort() {
    testMapPutGet((short)1, (short)2);
  }

  @Test
  public void testMapPutGetInt() {
    testMapPutGet(1, 2);
  }

  @Test
  public void testMapPutGetLong() {
    testMapPutGet(1l, 2l);
  }

  @Test
  public void testMapPutGetChar() {
    testMapPutGet('X', 'Y');
  }

  @Test
  public void testMapPutGetFloat() {
    testMapPutGet(1.2f, 2.2f);
  }

  @Test
  public void testMapPutGetBuffer() {
    testMapPutGet(randomBuffer(4), randomBuffer(12));
  }

  @Test
  public void testMapPutGetDouble() {
    testMapPutGet(1.2d, 2.2d);
  }

  @Test
  public void testMapPutGetBoolean() {
    testMapPutGet("foo", true);
  }

  @Test
  public void testMapPutGetString() {
    testMapPutGet("foo", "bar");
  }

  @Test
  public void testMapPutGetJsonObject() {
    testMapPutGet(new JsonObject().put("foo", "bar"), new JsonObject().put("uihwqduh", "qiwiojw"));
  }

  @Test
  public void testMapPutGetJsonArray() {
    testMapPutGet(new JsonArray().add("foo").add(2), new JsonArray().add("uihwqduh").add(false));
  }

  @Test
  public void testMapPutGetSerializableObject() {
    testMapPutGet(new SomeSerializableObject("bar"), new SomeSerializableObject("bar"));
  }

  @Test
  public void testMapPutGetClusterSerializableObject() {
    testMapPutGet(new SomeClusterSerializableObject("bar"), new SomeClusterSerializableObject("bar"));
  }

  @Test
  public void testMapPutGetClusterSerializableImplObject() {
    testMapPutGet(new SomeClusterSerializableImplObject("bar"), new SomeClusterSerializableImplObject("bar"));
  }

  @Test
  public void testMapPutTtl() {
    SharedData sharedData = getVertx().sharedData();
    sharedData
      .<String, String>getAsyncMap("foo").compose(map -> map.put("pipo", "molo", 10))
      .compose(v -> assertWaitUntilMapContains(sharedData, "foo", "pipo", 15, Objects::isNull))
      .onComplete(onSuccess(v -> testComplete()));
    await();
  }

  private Future<Void> assertWaitUntilMapContains(SharedData sharedData, String name, String key, long delay, Function<String, Boolean> checks) {
    return sharedData.<String, String>getAsyncMap(name).compose(map -> assertWaitUntil(map, key, delay, checks));
  }

  private Future<Void> assertWaitUntil(AsyncMap<String, String> map, String key, long delay, Function<String, Boolean> checks) {
    return Future.future(p -> assertWaitUntil(map, key, delay, checks, p));
  }

  private void assertWaitUntil(AsyncMap<String, String> map, String key, long delay, Function<String, Boolean> checks, Promise<Void> promise) {
    vertx.setTimer(delay, l -> {
      map.get(key)
        .onComplete(onSuccess(value -> {
          if (checks.apply(value)) {
            promise.complete();
          } else {
            assertWaitUntil(map, key, delay, checks, promise);
          }
        }));
    });
  }

  @Test
  public void testMapPutTtlThenPut() {
    SharedData sharedData = getVertx().sharedData();
    sharedData.<String, String>getAsyncMap("foo")
      .compose(map -> map
        .put("pipo", "molo", 10)
        .compose(v -> map.put("pipo", "mili")))
      .compose(v -> assertWaitUntilMapContains(sharedData, "foo", "pipo", 20, s -> "mili".equals(s)))
      .onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testMapPutThenPutTtl() {
    SharedData sharedData = getVertx().sharedData();
    sharedData.<String, String>getAsyncMap("foo")
      .compose(map -> map
        .put("pipo", "molo")
        .compose(v -> map.put("pipo", "mili", 10)))
      .compose(v -> assertWaitUntilMapContains(sharedData, "foo", "pipo", 15, Objects::isNull))
      .onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testMapPutIfAbsentGetByte() {
    testMapPutIfAbsentGet((byte)1, (byte)2);
  }

  @Test
  public void testMapPutIfAbsentGetShort() {
    testMapPutIfAbsentGet((short)1, (short)2);
  }

  @Test
  public void testMapPutIfAbsentGetInt() {
    testMapPutIfAbsentGet(1, 2);
  }

  @Test
  public void testMapPutIfAbsentGetLong() {
    testMapPutIfAbsentGet(1l, 2l);
  }

  @Test
  public void testMapPutIfAbsentGetChar() {
    testMapPutIfAbsentGet('X', 'Y');
  }

  @Test
  public void testMapPutIfAbsentGetFloat() {
    testMapPutIfAbsentGet(1.2f, 2.2f);
  }

  @Test
  public void testMapPutIfAbsentGetBuffer() {
    testMapPutIfAbsentGet(randomBuffer(4), randomBuffer(12));
  }

  @Test
  public void testMapPutIfAbsentGetDouble() {
    testMapPutIfAbsentGet(1.2d, 2.2d);
  }

  @Test
  public void testMapPutIfAbsentGetBoolean() {
    testMapPutIfAbsentGet("foo", true);
  }

  @Test
  public void testMapPutIfAbsentGetString() {
    testMapPutIfAbsentGet("foo", "bar");
  }

  @Test
  public void testMapPutIfAbsentGetJsonObject() {
    testMapPutIfAbsentGet(new JsonObject().put("foo", "bar"), new JsonObject().put("uihwqduh", "qiwiojw"));
  }

  @Test
  public void testMapPutIfAbsentGetJsonArray() {
    testMapPutIfAbsentGet(new JsonArray().add("foo").add(2), new JsonArray().add("uihwqduh").add(false));
  }

  @Test
  public void testMapPutIfAbsentGetSerializableObject() {
    testMapPutIfAbsentGet(new SomeSerializableObject("bar"), new SomeSerializableObject("bar"));
  }

  @Test
  public void testMapPutIfAbsentGetClusterSerializableObject() {
    testMapPutIfAbsentGet(new SomeClusterSerializableObject("bar"), new SomeClusterSerializableObject("bar"));
  }

  @Test
  public void testMapPutIfAbsentGetClusterSerializableImplObject() {
    testMapPutIfAbsentGet(new SomeClusterSerializableImplObject("bar"), new SomeClusterSerializableImplObject("bar"));
  }

  @Test
  public void testMapPutIfAbsentTtl() {
    SharedData sharedData = getVertx().sharedData();
    sharedData.<String, String>getAsyncMap("foo")
      .compose(map -> map.putIfAbsent("pipo", "molo", 10).andThen(onSuccess(this::assertNull)))
      .compose(v -> assertWaitUntilMapContains(sharedData, "foo", "pipo", 15, Objects::isNull))
      .onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testMapPutIfAbsentTtlWithExistingNotGettingDeleted() {
    SharedData sharedData = getVertx().sharedData();
    sharedData.<String, String>getAsyncMap("foo")
      .compose(map -> map
        .put("pipo", "molo")
        .compose(v -> map
          .putIfAbsent("pipo", "mili", 10)
          .andThen(onSuccess(vd -> assertEquals("molo", vd)))))
      .compose(v -> assertWaitUntilMapContains(sharedData, "foo", "pipo", 15, "molo"::equals)).onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testMapRemoveByte() {
    testMapRemove((byte)1, (byte)2);
  }

  @Test
  public void testMapRemoveShort() {
    testMapRemove((short)1, (short)2);
  }

  @Test
  public void testMapRemoveInt() {
    testMapRemove(1, 2);
  }

  @Test
  public void testMapRemoveLong() {
    testMapRemove(1l, 2l);
  }

  @Test
  public void testMapRemoveChar() {
    testMapRemove('X', 'Y');
  }

  @Test
  public void testMapRemoveFloat() {
    testMapRemove(1.2f, 2.2f);
  }

  @Test
  public void testMapRemoveBuffer() {
    testMapRemove(randomBuffer(4), randomBuffer(12));
  }

  @Test
  public void testMapRemoveDouble() {
    testMapRemove(1.2d, 2.2d);
  }

  @Test
  public void testMapRemoveBoolean() {
    testMapRemove("foo", true);
  }

  @Test
  public void testMapRemoveString() {
    testMapRemove("foo", "bar");
  }

  @Test
  public void testMapRemoveJsonObject() {
    testMapRemove(new JsonObject().put("foo", "bar"), new JsonObject().put("uihwqduh", "qiwiojw"));
  }

  @Test
  public void testMapRemoveJsonArray() {
    testMapRemove(new JsonArray().add("foo").add(2), new JsonArray().add("uihwqduh").add(false));
  }

  @Test
  public void testMapRemoveSerializableObject() {
    testMapRemove(new SomeSerializableObject("bar"), new SomeSerializableObject("bar"));
  }

  @Test
  public void testMapRemoveClusterSerializableObject() {
    testMapRemove(new SomeClusterSerializableObject("bar"), new SomeClusterSerializableObject("bar"));
  }

  @Test
  public void testMapRemoveClusterSerializableImplObject() {
    testMapRemove(new SomeClusterSerializableImplObject("bar"), new SomeClusterSerializableImplObject("bar"));
  }

  @Test
  public void testMapRemoveIfPresentByte() {
    testMapRemoveIfPresent((byte) 1, (byte) 2, (byte) 3, (byte) 4);
  }

  @Test
  public void testMapRemoveIfPresentShort() {
    testMapRemoveIfPresent((short) 1, (short) 2, (short) 3, (short) 4);
  }

  @Test
  public void testMapRemoveIfPresentInt() {
    testMapRemoveIfPresent(1, 2, 3, 4);
  }

  @Test
  public void testMapRemoveIfPresentLong() {
    testMapRemoveIfPresent(1l, 2l, 3l, 4l);
  }

  @Test
  public void testMapRemoveIfPresentChar() {
    testMapRemoveIfPresent('W', 'X', 'Y', 'Z');
  }

  @Test
  public void testMapRemoveIfPresentFloat() {
    testMapRemoveIfPresent(1.2f, 2.2f, 3.2f, 4.2f);
  }

  @Test
  public void testMapRemoveIfPresentBuffer() {
    testMapRemoveIfPresent(randomBuffer(4), randomBuffer(4), randomBuffer(12), randomBuffer(14));
  }

  @Test
  public void testMapRemoveIfPresentDouble() {
    testMapRemoveIfPresent(1.2d, 2.2d, 3.2d, 4.2d);
  }

  @Test
  public void testMapRemoveIfPresentBoolean() {
    testMapRemoveIfPresent("foo", "bar", true, false);
  }

  @Test
  public void testMapRemoveIfPresentString() {
    testMapRemoveIfPresent("foo", "bar", "baz", "quux");
  }

  @Test
  public void testMapRemoveIfPresentJsonObject() {
    testMapRemoveIfPresent(
      new JsonObject().put("foo", "bar"), new JsonObject().put("baz", "quux"),
      new JsonObject().put("uihwqduh", "qiwiojw"), new JsonObject().put("regerg", "wfwef"));
  }

  @Test
  public void testMapRemoveIfPresentJsonArray() {
    testMapRemoveIfPresent(
      new JsonArray().add("foo").add(2), new JsonArray().add("bar").add(4),
      new JsonArray().add("uihwqduh").add(false), new JsonArray().add("qqddq").add(true));
  }

  @Test
  public void testMapRemoveIfPresentSerializableObject() {
    testMapRemoveIfPresent(new SomeSerializableObject("foo"), new SomeSerializableObject("bar"), new SomeSerializableObject("baz"), new SomeSerializableObject("quux"));
  }

  @Test
  public void testMapRemoveIfPresentClusterSerializableObject() {
    testMapRemoveIfPresent(new SomeClusterSerializableObject("foo"), new SomeClusterSerializableObject("bar"), new SomeClusterSerializableObject("baz"), new SomeClusterSerializableObject("quux"));
  }

  @Test
  public void testMapRemoveIfPresentClusterSerializableImplObject() {
    testMapRemoveIfPresent(new SomeClusterSerializableImplObject("foo"), new SomeClusterSerializableImplObject("bar"), new SomeClusterSerializableImplObject("baz"), new SomeClusterSerializableImplObject("quux"));
  }

  @Test
  public void testMapReplaceByte() {
    testMapReplace((byte) 1, (byte) 2, (byte) 3);
  }

  @Test
  public void testMapReplaceShort() {
    testMapReplace((short) 1, (short) 2, (short) 3);
  }

  @Test
  public void testMapReplaceInt() {
    testMapReplace(1, 2, 3);
  }

  @Test
  public void testMapReplaceLong() {
    testMapReplace(1l, 2l, 3l);
  }

  @Test
  public void testMapReplaceChar() {
    testMapReplace('X', 'Y', 'Z');
  }

  @Test
  public void testMapReplaceFloat() {
    testMapReplace(1.2f, 2.2f, 3.3f);
  }

  @Test
  public void testMapReplaceBuffer() {
    testMapReplace(randomBuffer(4), randomBuffer(12), randomBuffer(14));
  }

  @Test
  public void testMapReplaceDouble() {
    testMapReplace(1.2d, 2.2d, 3.3d);
  }

  @Test
  public void testMapReplaceBoolean() {
    testMapReplace("foo", true, false);
  }

  @Test
  public void testMapReplaceString() {
    testMapReplace("foo", "bar", "quux");
  }

  @Test
  public void testMapReplaceJsonObject() {
    testMapReplace(new JsonObject().put("foo", "bar"), new JsonObject().put("uihwqduh", "qiwiojw"),
      new JsonObject().put("regerg", "wfwef"));
  }

  @Test
  public void testMapReplaceJsonArray() {
    testMapReplace(new JsonArray().add("foo").add(2), new JsonArray().add("uihwqduh").add(false),
      new JsonArray().add("qqddq").add(true));
  }

  @Test
  public void testMapReplaceSerializableObject() {
    testMapReplace(new SomeSerializableObject("foo"), new SomeSerializableObject("bar"), new SomeSerializableObject("quux"));
  }

  @Test
  public void testMapReplaceClusterSerializableObject() {
    testMapReplace(new SomeClusterSerializableObject("foo"), new SomeClusterSerializableObject("bar"), new SomeClusterSerializableObject("quux"));
  }

  @Test
  public void testMapReplaceClusterSerializableImplObject() {
    testMapReplace(new SomeClusterSerializableImplObject("foo"), new SomeClusterSerializableImplObject("bar"), new SomeClusterSerializableImplObject("quux"));
  }

  @Test
  public void testMapReplaceTtl() {
    SharedData sharedData = getVertx().sharedData();
    sharedData.<String, String>getAsyncMap("foo")
      .compose(map -> map
        .replace("pipo", "molo", 10)
        .andThen(onSuccess(this::assertNull)))
      .compose(v -> assertWaitUntilMapContains(sharedData, "foo", "pipo", 15, Objects::isNull))
      .onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testMapReplaceTtlWithPreviousValue() {
    SharedData sharedData = getVertx().sharedData();
    sharedData.<String, String>getAsyncMap("foo")
      .compose(map -> map
        .put("pipo", "molo").andThen(onSuccess(this::assertNull))
        .compose(vd -> map.replace("pipo", "mili", 10)))
      .compose(v -> assertWaitUntilMapContains(sharedData, "foo", "pipo", 15, Objects::isNull))
      .onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testMapReplaceIfPresentByte() {
    testMapReplaceIfPresent((byte)1, (byte)2, (byte)3);
  }

  @Test
  public void testMapReplaceIfPresentShort() {
    testMapReplaceIfPresent((short)1, (short)2, (short)3);
  }

  @Test
  public void testMapReplaceIfPresentInt() {
    testMapReplaceIfPresent(1, 2, 3);
  }

  @Test
  public void testMapReplaceIfPresentLong() {
    testMapReplaceIfPresent(1l, 2l, 3l);
  }

  @Test
  public void testMapReplaceIfPresentChar() {
    testMapReplaceIfPresent('X', 'Y', 'Z');
  }

  @Test
  public void testMapReplaceIfPresentFloat() {
    testMapReplaceIfPresent(1.2f, 2.2f, 3.3f);
  }

  @Test
  public void testMapReplaceIfPresentBuffer() {
    testMapReplaceIfPresent(randomBuffer(4), randomBuffer(12), randomBuffer(14));
  }

  @Test
  public void testMapReplaceIfPresentDouble() {
    testMapReplaceIfPresent(1.2d, 2.2d, 3.3d);
  }

  @Test
  public void testMapReplaceIfPresentBoolean() {
    testMapReplaceIfPresent("foo", true, false);
  }

  @Test
  public void testMapReplaceIfPresentString() {
    testMapReplaceIfPresent("foo", "bar", "quux");
  }

  @Test
  public void testMapReplaceIfPresentJsonObject() {
    testMapReplaceIfPresent(new JsonObject().put("foo", "bar"), new JsonObject().put("uihwqduh", "qiwiojw"),
      new JsonObject().put("regerg", "wfwef"));
  }

  @Test
  public void testMapReplaceIfPresentJsonArray() {
    testMapReplaceIfPresent(new JsonArray().add("foo").add(2), new JsonArray().add("uihwqduh").add(false),
      new JsonArray().add("qqddq").add(true));
  }

  @Test
  public void testMapReplaceIfPresentSerializableObject() {
    testMapReplaceIfPresent(new SomeSerializableObject("foo"), new SomeSerializableObject("bar"), new SomeSerializableObject("quux"));
  }

  @Test
  public void testMapReplaceIfPresentClusterSerializableObject() {
    testMapReplaceIfPresent(new SomeClusterSerializableObject("foo"), new SomeClusterSerializableObject("bar"), new SomeClusterSerializableObject("quux"));
  }

  @Test
  public void testMapReplaceIfPresentClusterSerializableImplObject() {
    testMapReplaceIfPresent(new SomeClusterSerializableImplObject("foo"), new SomeClusterSerializableImplObject("bar"), new SomeClusterSerializableImplObject("quux"));
  }

  @Test
  public void testMapReplaceIfPresentTtl() {
    SharedData sharedData = getVertx().sharedData();
    sharedData.<String, String>getAsyncMap("foo")
      .compose(map -> map
        .put("pipo", "molo").andThen(onSuccess(this::assertNull))
        .compose(v -> map.replaceIfPresent("pipo", "molo", "mili", 10).andThen(onSuccess(this::assertTrue))))
      .compose(v -> assertWaitUntilMapContains(sharedData, "foo", "pipo", 15, Objects::isNull))
      .onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testMapReplaceIfPresentTtlWhenNotPresent() {
    getVertx().sharedData().<String, String>getAsyncMap("foo")
      .compose(map -> map
        .replaceIfPresent("pipo", "molo", "mili",10)
        .andThen(onSuccess(this::assertFalse)))
      .onComplete(onSuccess(vd -> testComplete()));
    await();
  }

  @Test
  public void testGetMapWithNullName() throws Exception {
    assertNullPointerException(() -> getVertx().sharedData().<String, String>getAsyncMap(null));
  }

  @Test
  public void testPutNullKey() {
    getVertx().sharedData().<String, String>getAsyncMap("foo")
      .onComplete(onSuccess(map -> {
        assertIllegalArgumentException(() -> map.put(null, "foo"));
        testComplete();
      }));
    await();
  }

  @Test
  public void testPutNullValue() {
    getVertx().sharedData().<String, String>getAsyncMap("foo")
      .onComplete(onSuccess(map -> {
        assertIllegalArgumentException(() -> map.put("foo", null));
        testComplete();
      }));
    await();
  }

  @Test
  public void testPutInvalidKey() {
    getVertx().sharedData().<SomeObject, String>getAsyncMap("foo")
      .onComplete(onSuccess(map -> {
        assertIllegalArgumentException(() -> map.put(new SomeObject(), "foo"));
        testComplete();
      }));
    await();
  }

  @Test
  public void testPutInvalidValue() {
    getVertx().sharedData().<String, SomeObject>getAsyncMap("foo")
      .onComplete(onSuccess(map -> {
        assertIllegalArgumentException(() -> map.put("foo", new SomeObject()));
        testComplete();
      }));
    await();
  }

  @Test
  public void testPutIfAbsentInvalidKey() {
    getVertx().sharedData().<SomeObject, String>getAsyncMap("foo")
      .onComplete(onSuccess(map -> {
        assertIllegalArgumentException(() -> map.putIfAbsent(new SomeObject(), "foo"));
        testComplete();
      }));
    await();
  }

  @Test
  public void testPutIfAbsentInvalidValue() {
    getVertx().sharedData().<String, SomeObject>getAsyncMap("foo")
      .onComplete(onSuccess(map -> {
        assertIllegalArgumentException(() -> map.putIfAbsent("foo", new SomeObject()));
        testComplete();
      }));
    await();
  }

  @Test
  public void testMultipleMaps() {
    SharedData sharedData = getVertx().sharedData();
    sharedData.<String, String>getAsyncMap("foo")
      .compose(map -> map.put("foo", "bar"))
      .compose(v -> sharedData
        .<String, String>getAsyncMap("bar")
        .compose(map -> map.get("foo"))
        .andThen(onSuccess(this::assertNull)))
      .onComplete(onSuccess(res -> testComplete()));
    await();
  }

  @Test
  public void testClear() {
    SharedData sharedData = getVertx().sharedData();
    sharedData.<String, String>getAsyncMap("foo")
      .compose(map -> map.put("foo", "bar"))
      .compose(v -> sharedData
        .<String, String>getAsyncMap("foo")
        .compose(map -> map
          .clear()
          .compose(v2 -> map.get("foo"))))
      .onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testSize() {
    SharedData sharedData = getVertx().sharedData();
    sharedData.<String, String>getAsyncMap("foo")
      .compose(map -> map
        .size().andThen(onSuccess(size -> assertEquals(0, size.intValue())))
        .compose(v -> map.put("foo", "bar"))
        .compose(v -> map.size().andThen(onSuccess(size -> assertEquals(1, size.intValue())))))
      .compose(v -> sharedData.
          <String, String>getAsyncMap("foo")
          .compose(map -> map.size().andThen(onSuccess(size -> assertEquals(1, size.intValue())))))
      .onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testKeys() {
    Map<JsonObject, Buffer> map = genJsonToBuffer(100);
    loadData(map, (vertx, asyncMap) -> {
      asyncMap.keys()
        .onComplete(onSuccess(keys -> {
          assertEquals(map.keySet(), keys);
          testComplete();
        }));
    });
    await();
  }

  @Test
  public void testValues() {
    Map<JsonObject, Buffer> map = genJsonToBuffer(100);
    loadData(map, (vertx, asyncMap) -> {
      asyncMap
        .values()
        .andThen(onSuccess(values -> {
          assertEquals(map.values().size(), values.size());
          assertTrue(map.values().containsAll(values));
          assertTrue(values.containsAll(map.values()));
        }))
        .onComplete(onSuccess(values -> testComplete()));
    });
    await();
  }

  @Test
  public void testEntries() {
    Map<JsonObject, Buffer> map = genJsonToBuffer(100);
    loadData(map, (vertx, asyncMap) -> {
      asyncMap
        .entries().andThen(onSuccess(res -> assertEquals(map.entrySet(), res.entrySet())))
        .onSuccess(res -> testComplete());
    });
    await();
  }

  protected Map<JsonObject, Buffer> genJsonToBuffer(int size) {
    Map<JsonObject, Buffer> map = new HashMap<>();
    for (int i = 0; i < size; i++) {
      JsonObject key = new JsonObject().put("key", i);
      map.put(key, key.toBuffer());
    }
    return map;
  }

  protected void loadData(Map<JsonObject, Buffer> map, BiConsumer<Vertx, AsyncMap<JsonObject, Buffer>> test) {
    SharedData sharedData = getVertx().sharedData();
    List<Future<?>> futures = new ArrayList<>(map.size());
    map.forEach((key, value) -> {
      futures.add(sharedData.getAsyncMap("foo").compose(asyncMap -> asyncMap.put(key, value)));
    });
    CompositeFuture.all(futures).compose(cf -> sharedData.<JsonObject, Buffer>getAsyncMap("foo"))
      .onComplete(onSuccess(asyncMap -> test.accept(getVertx(), asyncMap)));
  }

  private <K, V> void testMapPutGet(K k, V v) {
    SharedData sharedData = getVertx().sharedData();
    sharedData.<K, V>getAsyncMap("foo")
      .compose(map -> map.put(k, v))
      .compose(v_ -> sharedData
        .<K, V>getAsyncMap("foo")
        .compose(map -> map
          .get(k))
        .andThen(onSuccess(res -> assertEquals(v, res))))
      .onComplete(onSuccess(res -> testComplete()));
    await();
  }

  private <K, V> void testMapPutIfAbsentGet(K k, V v) {
    SharedData sharedData = getVertx().sharedData();
    sharedData.<K, V>getAsyncMap("foo")
      .compose(map -> map.putIfAbsent(k, v))
      .andThen(onSuccess(this::assertNull))
      .compose(v_ -> sharedData.<K, V>getAsyncMap("foo"))
      .compose(map -> map
        .get(k)
        .andThen(onSuccess(res -> assertEquals(v, res)))
        .compose(res2 -> map.putIfAbsent(k, v))
        .andThen(onSuccess(res -> assertEquals(v, res))))
      .onComplete(onSuccess(res -> testComplete()));
    await();
  }

  private <K, V> void testMapRemove(K k, V v) {
    SharedData sharedData = getVertx().sharedData();
    sharedData.<K, V>getAsyncMap("foo")
      .compose(map -> map.put(k, v).andThen(onSuccess(this::assertNull)))
      .compose(v_ -> sharedData
        .<K, V>getAsyncMap("foo")
        .compose(map -> map.remove(k))
        .andThen(onSuccess(res -> assertEquals(v, res))))
      .onComplete(onSuccess(res -> testComplete()));
    await();
  }

  private <K, V> void testMapRemoveIfPresent(K k, K otherKey, V v, V otherValue) {
    SharedData sharedData = getVertx().sharedData();
    sharedData
      .<K, V>getAsyncMap("foo")
      .compose(map -> map.put(k, v).andThen(onSuccess(this::assertNull)))
      .compose(v_ -> sharedData.<K, V>getAsyncMap("foo"))
      .compose(map -> map
        .removeIfPresent(otherKey, v)
        .andThen(onSuccess(this::assertFalse))
        .compose(res -> map.removeIfPresent(k, otherValue).andThen(onSuccess(this::assertFalse)))
        .compose(res -> map.removeIfPresent(k, v).andThen(onSuccess(this::assertTrue))))
      .onComplete(onSuccess(v_ -> testComplete()));
    await();
  }

  private <K, V> void testMapReplace(K k, V v, V other) {
    SharedData sharedData = getVertx().sharedData();
    sharedData
      .<K, V>getAsyncMap("foo")
      .compose(map -> map.put(k, v).andThen(onSuccess(this::assertNull)))
      .compose(v_ -> sharedData
        .<K, V>getAsyncMap("foo")
        .compose(map -> map.replace(k, other).andThen(onSuccess(res -> assertEquals(v, res)))
          .compose(v__ -> map.get(k).andThen(onSuccess(res -> assertEquals(other, res))))
          .compose(v__ -> map.remove(k)).compose(v__ -> map.replace(k, other).andThen(onSuccess(this::assertNull)))
          .compose(v__ -> map.get(k).andThen(onSuccess(this::assertNull)))))
      .onComplete(onSuccess(v_ -> testComplete()));

    await();
  }

  private <K, V> void testMapReplaceIfPresent(K k, V v, V other) {
    SharedData sharedData = getVertx().sharedData();
    sharedData.<K, V>getAsyncMap("foo")
      .compose(map -> map.put(k, v)
      .andThen(onSuccess(this::assertNull)))
      .compose(v_ -> sharedData.<K, V>getAsyncMap("foo"))
      .compose(map -> map
        .replaceIfPresent(k, v, other)
        .compose(res2 -> map.replaceIfPresent(k, v, other).andThen(onSuccess(this::assertFalse)))
        .compose(v_ -> map.get(k).andThen(onSuccess(res4 -> assertEquals(other, res4)))))
      .onComplete(onSuccess(v_ -> testComplete()));
    await();
  }

  public static final class SomeObject {
  }

  public static final class SomeSerializableObject implements Serializable {
    private String str;

    public SomeSerializableObject(String str) {
      this.str = str;
    }

    public SomeSerializableObject() {
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SomeSerializableObject)) return false;
      SomeSerializableObject that = (SomeSerializableObject) o;
      if (str != null ? !str.equals(that.str) : that.str != null) return false;
      return true;
    }

    @Override
    public int hashCode() {
      return str != null ? str.hashCode() : 0;
    }
  }

  public static final class SomeClusterSerializableObject implements ClusterSerializable {
    private String str;
    // Trick smart data grid marshallers: make sure ClusterSerializable methods are the only way to serialize and deserialize this class
    @SuppressWarnings("unused")
    private final Object object = new Object();

    public SomeClusterSerializableObject() {
    }

    public SomeClusterSerializableObject(String str) {
      this.str = str;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SomeClusterSerializableObject)) return false;
      SomeClusterSerializableObject that = (SomeClusterSerializableObject) o;
      if (str != null ? !str.equals(that.str) : that.str != null) return false;
      return true;
    }

    @Override
    public int hashCode() {
      return str != null ? str.hashCode() : 0;
    }

    @Override
    public void writeToBuffer(Buffer buffer) {
      buffer.appendInt(str.length());
      buffer.appendString(str);
    }

    @Override
    public int readFromBuffer(int pos, Buffer buffer) {
      int length = buffer.getInt(pos);
      str = buffer.getString(pos + 4, pos + 4 + length);
      return pos + 4 + length;
    }
  }

  @Deprecated
  public static final class SomeClusterSerializableImplObject implements io.vertx.core.shareddata.impl.ClusterSerializable {
    private String str;
    // Trick smart data grid marshallers: make sure ClusterSerializable methods are the only way to serialize and deserialize this class
    @SuppressWarnings("unused")
    private final Object object = new Object();

    public SomeClusterSerializableImplObject() {
    }

    public SomeClusterSerializableImplObject(String str) {
      this.str = str;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SomeClusterSerializableImplObject)) return false;
      SomeClusterSerializableImplObject that = (SomeClusterSerializableImplObject) o;
      if (str != null ? !str.equals(that.str) : that.str != null) return false;
      return true;
    }

    @Override
    public int hashCode() {
      return str != null ? str.hashCode() : 0;
    }

    @Override
    public void writeToBuffer(Buffer buffer) {
      buffer.appendInt(str.length());
      buffer.appendString(str);
    }

    @Override
    public int readFromBuffer(int pos, Buffer buffer) {
      int length = buffer.getInt(pos);
      str = buffer.getString(pos + 4, pos + 4 + length);
      return pos + 4 + length;
    }
  }
}
