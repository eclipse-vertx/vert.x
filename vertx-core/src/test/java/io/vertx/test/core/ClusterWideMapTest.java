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

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.fakecluster.FakeClusterManager;
import org.junit.Test;

import java.io.Serializable;

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
    testMapPutGet(new JsonObject().putString("foo", "bar"), new JsonObject().putString("uihwqduh", "qiwiojw"));
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
    testMapPutIfAbsentGet(new JsonObject().putString("foo", "bar"), new JsonObject().putString("uihwqduh", "qiwiojw"));
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
    testMapRemove(new JsonObject().putString("foo", "bar"), new JsonObject().putString("uihwqduh", "qiwiojw"));
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
    testMapRemoveIfPresent(new JsonObject().putString("foo", "bar"), new JsonObject().putString("uihwqduh", "qiwiojw"),
                           new JsonObject().putString("regerg", "wfwef"));
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
    testMapReplace(new JsonObject().putString("foo", "bar"), new JsonObject().putString("uihwqduh", "qiwiojw"),
      new JsonObject().putString("regerg", "wfwef"));
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
    testMapReplaceIfPresent(new JsonObject().putString("foo", "bar"), new JsonObject().putString("uihwqduh", "qiwiojw"),
      new JsonObject().putString("regerg", "wfwef"));
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
  public void testPutNullKey() {
    getVertx().sharedData().<String, String>getClusterWideMap("foo", ar -> {
      assertTrue(ar.succeeded());
      AsyncMap<String, String> map = ar.result();
      try {
        map.put(null, "foo", ar2 -> {});
        fail("Should throw Exception");
      } catch (IllegalArgumentException e) {
        // OK
        testComplete();
      }
    });
    await();
  }

  @Test
  public void testPutNullValue() {
    getVertx().sharedData().<String, String>getClusterWideMap("foo", ar -> {
      assertTrue(ar.succeeded());
      AsyncMap<String, String> map = ar.result();
      try {
        map.put("foo", null, ar2 -> {});
        fail("Should throw Exception");
      } catch (IllegalArgumentException e) {
        // OK
        testComplete();
      }
    });
    await();
  }

  @Test
  public void testPutInvalidKey() {
    getVertx().sharedData().<SomeObject, String>getClusterWideMap("foo", ar -> {
      assertTrue(ar.succeeded());
      AsyncMap<SomeObject, String> map = ar.result();
      try {
        map.put(new SomeObject(), "foo", ar2 -> {});
        fail("Should throw Exception");
      } catch (IllegalArgumentException e) {
        // OK
        testComplete();
      }
    });
    await();
  }

  @Test
  public void testPutInvalidValue() {
    getVertx().sharedData().<String, SomeObject>getClusterWideMap("foo", ar -> {
      assertTrue(ar.succeeded());
      AsyncMap<String, SomeObject> map = ar.result();
      try {
        map.put("foo", new SomeObject(), ar2 -> {});
        fail("Should throw Exception");
      } catch (IllegalArgumentException e) {
        // OK
        testComplete();
      }
    });
    await();
  }

  @Test
  public void testPutIfAbsentInvalidKey() {
    getVertx().sharedData().<SomeObject, String>getClusterWideMap("foo", ar -> {
      assertTrue(ar.succeeded());
      AsyncMap<SomeObject, String> map = ar.result();
      try {
        map.putIfAbsent(new SomeObject(), "foo", ar2 -> {
        });
        fail("Should throw Exception");
      } catch (IllegalArgumentException e) {
        // OK
        testComplete();
      }
    });
    await();
  }

  @Test
  public void testPutIfAbsentInvalidValue() {
    getVertx().sharedData().<String, SomeObject>getClusterWideMap("foo", ar -> {
      assertTrue(ar.succeeded());
      AsyncMap<String, SomeObject> map = ar.result();
      try {
        map.putIfAbsent("foo", new SomeObject(), ar2 -> {
        });
        fail("Should throw Exception");
      } catch (IllegalArgumentException e) {
        // OK
        testComplete();
      }
    });
    await();
  }

  @Test
  public void testMultipleMaps() {
    getVertx().sharedData().<String, String>getClusterWideMap("foo", ar -> {
      assertTrue(ar.succeeded());
      AsyncMap<String, String> map = ar.result();
      map.put("foo", "bar", ar2 -> {
        assertTrue(ar2.succeeded());
        getVertx().sharedData().<String, String>getClusterWideMap("bar", ar3 -> {
          AsyncMap<String, String> map2 = ar3.result();
          map2.get("foo", ar4 -> {
            assertNull(ar4.result());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testClear() {
    getVertx().sharedData().<String, String>getClusterWideMap("foo", ar -> {
      assertTrue(ar.succeeded());
      AsyncMap<String, String> map = ar.result();
      map.put("foo", "bar", ar2 -> {
        assertTrue(ar2.succeeded());
        getVertx().sharedData().<String, String>getClusterWideMap("foo", ar3 -> {
          AsyncMap<String, String> map2 = ar3.result();
          map2.clear(ar4 -> {
            assertTrue(ar4.succeeded());
            map.get("foo", ar5 -> {
              assertTrue(ar5.succeeded());
              assertNull(ar5.result());
              testComplete();
            });
          });
        });
      });
    });
    await();
  }

  private <K, V> void testMapPutGet(K k, V v) {
    getVertx().sharedData().<K, V>getClusterWideMap("foo", ar -> {
      assertTrue(ar.succeeded());
      AsyncMap<K, V> map = ar.result();
      map.put(k, v, ar2 -> {
        assertTrue(ar2.succeeded());
        getVertx().sharedData().<K, V>getClusterWideMap("foo", ar3 -> {
          AsyncMap<K, V> map2 = ar3.result();
          map2.get(k, ar4 -> {
            assertEquals(v, ar4.result());
            testComplete();
          });
        });
      });
    });
    await();
  }

  private <K, V> void testMapPutIfAbsentGet(K k, V v) {
    getVertx().sharedData().<K, V>getClusterWideMap("foo", ar -> {
      assertTrue(ar.succeeded());
      AsyncMap<K, V> map = ar.result();
      map.putIfAbsent(k, v, ar2 -> {
        assertTrue(ar2.succeeded());
        assertNull(ar2.result());
        getVertx().sharedData().<K, V>getClusterWideMap("foo", ar3 -> {
          AsyncMap<K, V> map2 = ar.result();
          map2.get(k, ar4 -> {
            assertEquals(v, ar4.result());
            map.putIfAbsent(k, v, ar5 -> {
              assertTrue(ar5.succeeded()); 
              assertEquals(v, ar5.result());
              testComplete();
            });                                    
          });
        });
      });
    });
    await();
  }

  private <K, V> void testMapRemove(K k, V v) {
    getVertx().sharedData().<K, V>getClusterWideMap("foo", ar -> {
      assertTrue(ar.succeeded());
      AsyncMap<K, V> map = ar.result();
      map.put(k, v, ar2 -> {
        assertTrue(ar2.succeeded());
        assertNull(ar2.result());
        getVertx().sharedData().<K, V>getClusterWideMap("foo", ar3 -> {
          AsyncMap<K, V> map2 = ar.result();
          map2.remove(k, ar4 -> {
            assertEquals(v, ar4.result());
            testComplete();
          });
        });
      });
    });
    await();
  }

  private <K, V> void testMapRemoveIfPresent(K k, V v, V other) {
    getVertx().sharedData().<K, V>getClusterWideMap("foo", ar -> {
      assertTrue(ar.succeeded());
      AsyncMap<K, V> map = ar.result();
      map.put(k, v, ar2 -> {
        assertTrue(ar2.succeeded());
        assertNull(ar2.result());
        getVertx().sharedData().<K, V>getClusterWideMap("foo", ar3 -> {
          AsyncMap<K, V> map2 = ar.result();
          map2.removeIfPresent(k, other, ar4 -> {
            assertFalse(ar4.result());
            map2.removeIfPresent(k, v, ar5 -> {
              assertTrue(ar5.result());
              testComplete();
            });
          });
        });
      });
    });
    await();
  }

  private <K, V> void testMapReplace(K k, V v, V other) {
    getVertx().sharedData().<K, V>getClusterWideMap("foo", ar -> {
      assertTrue(ar.succeeded());
      AsyncMap<K, V> map = ar.result();
      map.put(k, v, ar2 -> {
        assertTrue(ar2.succeeded());
        assertNull(ar2.result());
        getVertx().sharedData().<K, V>getClusterWideMap("foo", ar3 -> {
          AsyncMap<K, V> map2 = ar.result();
          map2.replace(k, other, ar4 -> {
            assertEquals(v, ar4.result());
            map2.get(k, ar5 -> {
              assertEquals(other, ar5.result());
              map2.remove(k, ar6 -> {
                assertTrue(ar6.succeeded());
                map2.replace(k, other, ar7 -> {
                  assertNull(ar7.result());
                  map2.get(k, ar8 -> {
                    assertNull(ar8.result());
                    testComplete();
                  });
                });
              });
            });
          });
        });
      });
    });
    await();
  }

  private <K, V> void testMapReplaceIfPresent(K k, V v, V other) {
    getVertx().sharedData().<K, V>getClusterWideMap("foo", ar -> {
      assertTrue(ar.succeeded());
      AsyncMap<K, V> map = ar.result();
      map.put(k, v, ar2 -> {
        assertTrue(ar2.succeeded());
        assertNull(ar2.result());
        getVertx().sharedData().<K, V>getClusterWideMap("foo", ar3 -> {
          AsyncMap<K, V> map2 = ar.result();
          map2.replaceIfPresent(k, v, other, ar4 -> {
            assertTrue(ar4.result());
            map2.replaceIfPresent(k, v, other, ar5 -> {
              assertFalse(ar5.result());
              map2.get(k, ar6 -> {
                assertEquals(other, ar6.result());
                testComplete();
              });
            });
          });
        });
      });
    });
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
