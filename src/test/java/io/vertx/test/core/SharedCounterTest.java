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
import io.vertx.core.shareddata.Counter;
import org.junit.Test;

import static io.vertx.test.core.TestUtils.assertNullPointerException;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SharedCounterTest extends VertxTestBase {

  protected Vertx getVertx() {
    return vertx;
  }

  @Test
  public void testIllegalArguments() throws Exception {
    assertNullPointerException(() -> getVertx().sharedData().getCounter(null, ar -> {}));
    assertNullPointerException(() -> getVertx().sharedData().getCounter("foo", null));
    getVertx().sharedData().getCounter("foo", ar -> {
      Counter counter = ar.result();
      assertNullPointerException(() -> counter.get(null));
      assertNullPointerException(() -> counter.incrementAndGet(null));
      assertNullPointerException(() -> counter.getAndIncrement(null));
      assertNullPointerException(() -> counter.decrementAndGet(null));
      assertNullPointerException(() -> counter.addAndGet(1, null));
      assertNullPointerException(() -> counter.getAndAdd(1, null));
      assertNullPointerException(() -> counter.compareAndSet(1, 1, null));
      testComplete();
    });
    await();
  }

  @Test
  public void testGet() {
    getVertx().sharedData().getCounter("foo", ar -> {
      assertTrue(ar.succeeded());
      Counter counter = ar.result();
      counter.get(ar2 -> {
        assertTrue(ar2.succeeded());
        assertEquals(0l, ar2.result().longValue());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testIncrementAndGet() {
    getVertx().sharedData().getCounter("foo", ar -> {
      assertTrue(ar.succeeded());
      Counter counter = ar.result();
      counter.incrementAndGet(ar2 -> {
        assertTrue(ar2.succeeded());
        assertEquals(1l, ar2.result().longValue());
        getVertx().sharedData().getCounter("foo", ar3 -> {
          assertTrue(ar3.succeeded());
          Counter counter2 = ar3.result();
          counter2.incrementAndGet(ar4 -> {
            assertTrue(ar4.succeeded());
            assertEquals(2l, ar4.result().longValue());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testGetAndIncrement() {
    getVertx().sharedData().getCounter("foo", ar -> {
      assertTrue(ar.succeeded());
      Counter counter = ar.result();
      counter.getAndIncrement(ar2 -> {
        assertTrue(ar2.succeeded());
        assertEquals(0l, ar2.result().longValue());
        getVertx().sharedData().getCounter("foo", ar3 -> {
          assertTrue(ar3.succeeded());
          Counter counter2 = ar3.result();
          counter2.getAndIncrement(ar4 -> {
            assertTrue(ar4.succeeded());
            assertEquals(1l, ar4.result().longValue());
            counter2.get(ar5 -> {
              assertTrue(ar5.succeeded());
              assertEquals(2l, ar5.result().longValue());
              testComplete();
            });
          });
        });
      });
    });
    await();
  }

  @Test
  public void testDecrementAndGet() {
    getVertx().sharedData().getCounter("foo", ar -> {
      assertTrue(ar.succeeded());
      Counter counter = ar.result();
      counter.decrementAndGet(ar2 -> {
        assertTrue(ar2.succeeded());
        assertEquals(-1l, ar2.result().longValue());
        getVertx().sharedData().getCounter("foo", ar3 -> {
          assertTrue(ar3.succeeded());
          Counter counter2 = ar3.result();
          counter2.decrementAndGet(ar4 -> {
            assertTrue(ar4.succeeded());
            assertEquals(-2l, ar4.result().longValue());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testAddAndGet() {
    getVertx().sharedData().getCounter("foo", ar -> {
      assertTrue(ar.succeeded());
      Counter counter = ar.result();
      counter.addAndGet(2, ar2 -> {
        assertTrue(ar2.succeeded());
        assertEquals(2l, ar2.result().longValue());
        getVertx().sharedData().getCounter("foo", ar3 -> {
          assertTrue(ar3.succeeded());
          Counter counter2 = ar3.result();
          counter2.addAndGet(2l, ar4 -> {
            assertTrue(ar4.succeeded());
            assertEquals(4l, ar4.result().longValue());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void getAndAdd() {
    getVertx().sharedData().getCounter("foo", ar -> {
      assertTrue(ar.succeeded());
      Counter counter = ar.result();
      counter.getAndAdd(2, ar2 -> {
        assertTrue(ar2.succeeded());
        assertEquals(0l, ar2.result().longValue());
        getVertx().sharedData().getCounter("foo", ar3 -> {
          assertTrue(ar3.succeeded());
          Counter counter2 = ar3.result();
          counter2.getAndAdd(2l, ar4 -> {
            assertTrue(ar4.succeeded());
            assertEquals(2l, ar4.result().longValue());
            counter2.get(ar5 -> {
              assertTrue(ar5.succeeded());
              assertEquals(4l, ar5.result().longValue());
              testComplete();
            });
          });
        });
      });
    });
    await();
  }

  @Test
  public void testCompareAndSet() {
    getVertx().sharedData().getCounter("foo", ar -> {
      assertTrue(ar.succeeded());
      Counter counter = ar.result();
      counter.compareAndSet(0l, 2l, onSuccess(result -> {
        getVertx().sharedData().getCounter("foo", ar3 -> {
          assertTrue(ar3.succeeded());
          Counter counter2 = ar3.result();
          counter2.compareAndSet(2l, 4l, ar4 -> {
            assertTrue(ar4.succeeded());
            assertTrue(ar4.result());
            counter2.compareAndSet(3l, 5l, ar5 -> {
              assertTrue(ar5.succeeded());
              assertFalse(ar5.result());
              testComplete();
            });
          });
        });
      }));
    });
    await();
  }

  @Test
  public void testDifferentCounters() {
    getVertx().sharedData().getCounter("foo", ar -> {
      assertTrue(ar.succeeded());
      Counter counter = ar.result();
      counter.incrementAndGet(onSuccess(res -> {
        assertEquals(1l, res.longValue());
        getVertx().sharedData().getCounter("bar", ar3 -> {
          assertTrue(ar3.succeeded());
          Counter counter2 = ar3.result();
          counter2.incrementAndGet(ar4 -> {
            assertEquals(1l, ar4.result().longValue());
            counter.incrementAndGet(ar5 -> {
              assertEquals(2l, ar5.result().longValue());
              testComplete();
            });
          });
        });
      }));
    });
    await();
  }

}
