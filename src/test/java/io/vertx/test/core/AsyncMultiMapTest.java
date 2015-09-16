/*
 * Copyright 2015 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.impl.ServerID;
import io.vertx.core.spi.cluster.AsyncMultiMap;
import io.vertx.core.spi.cluster.ClusterManager;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AsyncMultiMapTest extends VertxTestBase {

  protected ClusterManager clusterManager;
  protected volatile AsyncMultiMap<String, ServerID> map;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    startNodes(1);
    clusterManager = ((VertxInternal) vertices[0]).getClusterManager();
    CountDownLatch latch = new CountDownLatch(1);
    clusterManager.<String, ServerID>getAsyncMultiMap("mymap", onSuccess(res -> {
      map = res;
      latch.countDown();
    }));
    awaitLatch(latch);
  }

  @Test
  public void testMapAddGet() {
    ServerID serverID1 = new ServerID(1234, "foo.com");
    map.add("some-sub", serverID1, onSuccess(res -> {
      assertNull(res);
      ServerID serverID2 = new ServerID(4321, "blah.com");
      map.add("some-sub", serverID2, onSuccess(res2 -> {
        assertNull(res2);
        ServerID serverID3 = new ServerID(5432, "quux.com");
        map.add("some-sub2", serverID3, onSuccess(res3 -> {
          assertNull(res3);
          map.get("some-sub", onSuccess(res4 -> {
            Set<ServerID> set = new HashSet<>();
            for (ServerID sid : res4) {
              set.add(sid);
            }
            assertEquals(2, set.size());
            assertTrue(set.contains(serverID1));
            assertTrue(set.contains(serverID2));
            map.get("some-sub2", onSuccess(res5 -> {
              Set<ServerID> set2 = new HashSet<>();
              for (ServerID sid : res5) {
                set2.add(sid);
              }
              assertEquals(1, set2.size());
              assertTrue(set2.contains(serverID3));
              testComplete();
            }));
          }));
        }));

      }));
    }));
    await();
  }

  @Test
  public void testMapRemove() {
    ServerID serverID1 = new ServerID(1234, "foo.com");
    map.add("some-sub", serverID1, onSuccess(res -> {
      assertNull(res);
      ServerID serverID2 = new ServerID(4321, "blah.com");
      map.add("some-sub", serverID2, onSuccess(res2 -> {
        assertNull(res2);
        ServerID serverID3 = new ServerID(5432, "quux.com");
        map.add("some-sub2", serverID3, onSuccess(res3 -> {
          assertNull(res3);
          map.get("some-sub", onSuccess(res4 -> {
            Set<ServerID> set = new HashSet<>();
            for (ServerID sid : res4) {
              set.add(sid);
            }
            assertEquals(2, set.size());
            assertTrue(set.contains(serverID1));
            assertTrue(set.contains(serverID2));
            map.get("some-sub2", onSuccess(res5 -> {
              Set<ServerID> set2 = new HashSet<>();
              for (ServerID sid : res5) {
                set2.add(sid);
              }
              assertEquals(1, set2.size());
              assertTrue(set2.contains(serverID3));

              map.remove("some-sub2", serverID1, onSuccess(res6 -> {
                assertFalse(res6);
                map.remove("some-sub2", serverID3, onSuccess(res7 -> {

                  map.get("some-sub2", onSuccess(res8 -> {
                    Set<ServerID> set3 = new HashSet<>();
                    for (ServerID sid : res8) {
                      set3.add(sid);
                    }
                    assertEquals(0, set3.size());
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

  @Test
  public void testRemoveAllForValue() {
    ServerID serverID1 = new ServerID(1234, "foo.com");
    map.add("some-sub", serverID1, onSuccess(res -> {
      assertNull(res);
      ServerID serverID2 = new ServerID(4321, "blah.com");
      map.add("some-sub", serverID2, onSuccess(res2 -> {
        assertNull(res2);
        map.add("some-sub2", serverID1, onSuccess(res3 -> {
          assertNull(res3);
          map.removeAllForValue(serverID1, onSuccess(res4 -> {
            assertNull(res4);
            map.get("some-sub", onSuccess(res5 -> {
              Set<ServerID> set = new HashSet<>();
              for (ServerID sid : res5) {
                set.add(sid);
              }
              assertEquals(1, set.size());
              assertTrue(set.contains(serverID2));
              assertFalse(set.contains(serverID1));
              map.get("some-sub2", onSuccess(res6 -> {
                Set<ServerID> set2 = new HashSet<>();
                for (ServerID sid : res6) {
                  set2.add(sid);
                }
                assertEquals(0, set2.size());
                testComplete();
              }));
            }));
          }));
        }));
      }));
    }));
    await();
  }
}
