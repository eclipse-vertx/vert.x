/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static io.vertx.test.core.TestUtils.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ClusterWideMapTest extends VertxTestBase {

  protected int getNumNodes() {
    return 1;
  }

  protected Vertx getVertx() {
    return vertices[0];
  }

  public void setUp() throws Exception {
    super.setUp();
    startNodes(getNumNodes());
  }

  @Override
  protected ClusterManager getClusterManager() {
    return new FakeClusterManager();
  }

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
  public void testMapPutTtl() {
    getVertx().sharedData().<String, String>getClusterWideMap("foo", onSuccess(map -> {
      map.put("pipo", "molo", 10, onSuccess(vd -> {
        vertx.setTimer(10, l -> {
          getVertx().sharedData().<String, String>getClusterWideMap("foo", onSuccess(map2 -> {
            map2.get("pipo", onSuccess(res -> {
              assertNull(res);
              testComplete();
            }));
          }));
        });
      }));
    }));
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
  public void testMapPutIfAbsentTtl() {
    getVertx().sharedData().<String, String>getClusterWideMap("foo", onSuccess(map -> {
      map.putIfAbsent("pipo", "molo", 10, onSuccess(vd -> {
        assertNull(vd);
        vertx.setTimer(10, l -> {
          getVertx().sharedData().<String, String>getClusterWideMap("foo", onSuccess(map2 -> {
            map2.get("pipo", onSuccess(res -> {
              assertNull(res);
              testComplete();
            }));
          }));
        });
      }));
    }));
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
  public void testMapRemoveIfPresentByte() {
    testMapRemoveIfPresent((byte)1, (byte)2, (byte)3);
  }

  @Test
  public void testMapRemoveIfPresentShort() {
    testMapRemoveIfPresent((short)1, (short)2, (short)3);
  }

  @Test
  public void testMapRemoveIfPresentInt() {
    testMapRemoveIfPresent(1, 2, 3);
  }

  @Test
  public void testMapRemoveIfPresentLong() {
    testMapRemoveIfPresent(1l, 2l, 3l);
  }

  @Test
  public void testMapRemoveIfPresentChar() {
    testMapRemoveIfPresent('X', 'Y', 'Z');
  }

  @Test
  public void testMapRemoveIfPresentFloat() {
    testMapRemoveIfPresent(1.2f, 2.2f, 3.3f);
  }

  @Test
  public void testMapRemoveIfPresentBuffer() {
    testMapRemoveIfPresent(randomBuffer(4), randomBuffer(12), randomBuffer(14));
  }

  @Test
  public void testMapRemoveIfPresentDouble() {
    testMapRemoveIfPresent(1.2d, 2.2d, 3.3d);
  }

  @Test
  public void testMapRemoveIfPresentBoolean() {
    testMapRemoveIfPresent("foo", true, false);
  }

  @Test
  public void testMapRemoveIfPresentString() {
    testMapRemoveIfPresent("foo", "bar", "quux");
  }

  @Test
  public void testMapRemoveIfPresentJsonObject() {
    testMapRemoveIfPresent(new JsonObject().put("foo", "bar"), new JsonObject().put("uihwqduh", "qiwiojw"),
                           new JsonObject().put("regerg", "wfwef"));
  }

  @Test
  public void testMapRemoveIfPresentJsonArray() {
    testMapRemoveIfPresent(new JsonArray().add("foo").add(2), new JsonArray().add("uihwqduh").add(false),
                           new JsonArray().add("qqddq").add(true));
  }

  @Test
  public void testMapRemoveIfPresentSerializableObject() {
    testMapRemoveIfPresent(new SomeSerializableObject("foo"), new SomeSerializableObject("bar"), new SomeSerializableObject("quux"));
  }

  @Test
  public void testMapReplaceByte() {
    testMapReplace((byte)1, (byte)2, (byte)3);
  }

  @Test
  public void testMapReplaceShort() {
    testMapReplace((short)1, (short)2, (short)3);
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
  public void testGetMapWithNullName() throws Exception {
    assertNullPointerException(() -> getVertx().sharedData().<String, String>getClusterWideMap(null, ar -> {}));
  }

  @Test
  public void testGetMapWithNullResultHandler() throws Exception {
    assertNullPointerException(() -> getVertx().sharedData().<String, String>getClusterWideMap("foo", null));
  }

  @Test
  public void testPutNullKey() {
    getVertx().sharedData().<String, String>getClusterWideMap("foo", onSuccess(map -> {
      assertIllegalArgumentException(() -> map.put(null, "foo", ar2 -> {}));
      testComplete();
    }));
    await();
  }

  @Test
  public void testPutNullValue() {
    getVertx().sharedData().<String, String>getClusterWideMap("foo", onSuccess(map -> {
      assertIllegalArgumentException(() -> map.put("foo", null, ar2 -> {}));
      testComplete();
    }));
    await();
  }

  @Test
  public void testPutInvalidKey() {
    getVertx().sharedData().<SomeObject, String>getClusterWideMap("foo", onSuccess(map -> {
      assertIllegalArgumentException(() -> map.put(new SomeObject(), "foo", ar2 -> {}));
      testComplete();
    }));
    await();
  }

  @Test
  public void testPutInvalidValue() {
    getVertx().sharedData().<String, SomeObject>getClusterWideMap("foo", onSuccess(map -> {
      assertIllegalArgumentException(() -> map.put("foo", new SomeObject(), ar2 -> {}));
      testComplete();
    }));
    await();
  }

  @Test
  public void testPutIfAbsentInvalidKey() {
    getVertx().sharedData().<SomeObject, String>getClusterWideMap("foo", onSuccess(map -> {
      assertIllegalArgumentException(() -> map.putIfAbsent(new SomeObject(), "foo", ar2 -> {}));
      testComplete();
    }));
    await();
  }

  @Test
  public void testPutIfAbsentInvalidValue() {
    getVertx().sharedData().<String, SomeObject>getClusterWideMap("foo", onSuccess(map -> {
      assertIllegalArgumentException(() -> map.putIfAbsent("foo", new SomeObject(), ar2 -> {}));
      testComplete();
    }));
    await();
  }

  @Test
  public void testMultipleMaps() {
    getVertx().sharedData().<String, String>getClusterWideMap("foo", onSuccess(map -> {
      map.put("foo", "bar", onSuccess(v -> {
        getVertx().sharedData().<String, String>getClusterWideMap("bar", onSuccess(map2 -> {
          map2.get("foo", onSuccess(res -> {
            assertNull(res);
            testComplete();
          }));
        }));
      }));
    }));
    await();
  }

  @Test
  public void testClear() {
    getVertx().sharedData().<String, String>getClusterWideMap("foo", onSuccess(map -> {
      map.put("foo", "bar", onSuccess(v -> {
        getVertx().sharedData().<String, String>getClusterWideMap("foo", onSuccess(map2 -> {
          map2.clear(onSuccess(v2 -> {
            map.get("foo", onSuccess(res -> {
              assertNull(res);
              testComplete();
            }));
          }));
        }));
      }));
    }));
    await();
  }

  @Test
  public void testSize() {
    getVertx().sharedData().<String, String>getClusterWideMap("foo", onSuccess(map -> {
      map.size(onSuccess(size -> {
        assertEquals(0, size.intValue());
        map.put("foo", "bar", onSuccess(v -> {
          map.size(onSuccess(size2 -> {
            assertEquals(1, size2.intValue());
            getVertx().sharedData().<String, String>getClusterWideMap("foo", onSuccess(map2 -> {
              map2.size(onSuccess(size3 -> {
                assertEquals(1, size3.intValue());
                testComplete();
              }));
            }));
          }));
        }));
      }));
    }));
    await();
  }

  @Test
  public void testKeys() {
    Map<JsonObject, Buffer> map = genJsonToBuffer(100);
    loadData(map, (vertx, asyncMap) -> {
      asyncMap.keys(onSuccess(keys -> {
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
      asyncMap.values(onSuccess(values -> {
        assertEquals(map.values().size(), values.size());
        assertTrue(map.values().containsAll(values));
        assertTrue(values.containsAll(map.values()));
        testComplete();
      }));
    });
    await();
  }

  @Test
  public void testEntries() {
    Map<JsonObject, Buffer> map = genJsonToBuffer(100);
    loadData(map, (vertx, asyncMap) -> {
      asyncMap.entries(onSuccess(res -> {
        assertEquals(map.entrySet(), res.entrySet());
        testComplete();
      }));
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
    List<Future> futures = new ArrayList<>(map.size());
    map.forEach((key, value) -> {
      Future future = Future.future();
      getVertx().sharedData().getClusterWideMap("foo", onSuccess(asyncMap -> {
        asyncMap.put(key, value, future);
      }));
      futures.add(future);
    });
    CompositeFuture.all(futures).setHandler(onSuccess(cf -> {
      Vertx v = getVertx();
      v.sharedData().<JsonObject, Buffer>getClusterWideMap("foo", onSuccess(asyncMap -> {
        test.accept(v, asyncMap);
      }));
    }));
  }

  private <K, V> void testMapPutGet(K k, V v) {
    getVertx().sharedData().<K, V>getClusterWideMap("foo", onSuccess(map -> {
      map.put(k, v, onSuccess(vd -> {
        getVertx().sharedData().<K, V>getClusterWideMap("foo", onSuccess(map2 -> {
          map2.get(k, onSuccess(res -> {
            assertEquals(v, res);
            testComplete();
          }));
        }));
      }));
    }));
    await();
  }

  private <K, V> void testMapPutIfAbsentGet(K k, V v) {
    getVertx().sharedData().<K, V>getClusterWideMap("foo", onSuccess(map -> {
      map.putIfAbsent(k, v, onSuccess(res -> {
        assertNull(res);
        getVertx().sharedData().<K, V>getClusterWideMap("foo", onSuccess(map2 -> {
          map2.get(k, onSuccess(res2 -> {
            assertEquals(v, res2);
            map.putIfAbsent(k, v, onSuccess(res3 -> {
              assertEquals(v, res3);
              testComplete();
            }));
          }));
        }));
      }));
    }));
    await();
  }

  private <K, V> void testMapRemove(K k, V v) {
    getVertx().sharedData().<K, V>getClusterWideMap("foo", onSuccess(map -> {
      map.put(k, v, onSuccess(res -> {
        assertNull(res);
        getVertx().sharedData().<K, V>getClusterWideMap("foo", onSuccess(map2 -> {
          map2.remove(k, onSuccess(res2 -> {
            assertEquals(v, res2);
            testComplete();
          }));
        }));
      }));
    }));
    await();
  }

  private <K, V> void testMapRemoveIfPresent(K k, V v, V other) {
    getVertx().sharedData().<K, V>getClusterWideMap("foo", onSuccess(map -> {
      map.put(k, v, onSuccess(res -> {
        assertNull(res);
        getVertx().sharedData().<K, V>getClusterWideMap("foo", onSuccess(map2 -> {
          map2.removeIfPresent(k, other, onSuccess(res2 -> {
            assertFalse(res2);
            map2.removeIfPresent(k, v, onSuccess(res3 -> {
              assertTrue(res3);
              testComplete();
            }));
          }));
        }));
      }));
    }));
    await();
  }

  private <K, V> void testMapReplace(K k, V v, V other) {
    getVertx().sharedData().<K, V>getClusterWideMap("foo", onSuccess(map -> {
      map.put(k, v, onSuccess(res -> {
        assertNull(res);
        getVertx().sharedData().<K, V>getClusterWideMap("foo", onSuccess(map2 -> {
          map2.replace(k, other, onSuccess(res2 -> {
            assertEquals(v, res2);
            map2.get(k, onSuccess(res3 -> {
              assertEquals(other, res3);
              map2.remove(k, onSuccess(res4 -> {
                map2.replace(k, other, onSuccess(res5 -> {
                  assertNull(res5);
                  map2.get(k, onSuccess(res6 -> {
                    assertNull(res6);
                    testComplete();
                  }));
                }));
              }));
            }));
          }));
        }));
      }));
    }));
    await();
  }

  private <K, V> void testMapReplaceIfPresent(K k, V v, V other) {
    getVertx().sharedData().<K, V>getClusterWideMap("foo", onSuccess(map -> {
      map.put(k, v, onSuccess(res -> {
        assertNull(res);
        getVertx().sharedData().<K, V>getClusterWideMap("foo", onSuccess(map2 -> {
          map2.replaceIfPresent(k, v, other, onSuccess(res2 -> {
            map2.replaceIfPresent(k, v, other, onSuccess(res3 -> {
              assertFalse(res3);
              map2.get(k, onSuccess(res4 -> {
                assertEquals(other, res4);
                testComplete();
              }));
            }));
          }));
        }));
      }));
    }));
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
}
