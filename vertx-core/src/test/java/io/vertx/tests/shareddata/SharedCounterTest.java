/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tests.shareddata;

import io.vertx.core.Vertx;
import io.vertx.test.core.VertxTestBase;
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
    assertNullPointerException(() -> getVertx().sharedData().getCounter(null));
  }

  @Test
  public void testGet() {
    getVertx().sharedData().getCounter("foo").onComplete(onSuccess(counter -> {
      counter.get().onComplete(onSuccess(res -> {
        assertEquals(0L, res.longValue());
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testIncrementAndGet() {
    getVertx().sharedData().getCounter("foo").onComplete(onSuccess(counter -> {
      counter.incrementAndGet().onComplete(onSuccess(res1 -> {
        assertEquals(1L, res1.longValue());
        getVertx().sharedData().getCounter("foo").onComplete(onSuccess(counter2 -> {
          counter2.incrementAndGet().onComplete(onSuccess(res2 -> {
            assertEquals(2L, res2.longValue());
            testComplete();
          }));
        }));
      }));
    }));
    await();
  }

  @Test
  public void testGetAndIncrement() {
    getVertx().sharedData().getCounter("foo").onComplete(onSuccess(counter -> {
      counter.getAndIncrement().onComplete(onSuccess(res -> {
        assertEquals(0L, res.longValue());
        getVertx().sharedData().getCounter("foo").onComplete(onSuccess(counter2 -> {
          counter2.getAndIncrement().onComplete(onSuccess(res2 -> {
            assertEquals(1L, res2.longValue());
            counter2.get().onComplete(onSuccess(res3 -> {
              assertEquals(2L, res3.longValue());
              testComplete();
            }));
          }));
        }));
      }));
    }));
    await();
  }

  @Test
  public void testDecrementAndGet() {
    getVertx().sharedData().getCounter("foo").onComplete(onSuccess(counter -> {
      counter.decrementAndGet().onComplete(onSuccess(res -> {
        assertEquals(-1L, res.longValue());
        getVertx().sharedData().getCounter("foo").onComplete(onSuccess(counter2 -> {
          counter2.decrementAndGet().onComplete(onSuccess(res2 -> {
            assertEquals(-2L, res2.longValue());
            testComplete();
          }));
        }));
      }));
    }));
    await();
  }

  @Test
  public void testAddAndGet() {
    getVertx().sharedData().getCounter("foo").onComplete(onSuccess(counter -> {
      counter.addAndGet(2).onComplete(onSuccess(res -> {
        assertEquals(2L, res.longValue());
        getVertx().sharedData().getCounter("foo").onComplete(onSuccess(counter2 -> {
          counter2.addAndGet(2L).onComplete(onSuccess(res2 -> {
            assertEquals(4L, res2.longValue());
            testComplete();
          }));
        }));
      }));
    }));
    await();
  }

  @Test
  public void getAndAdd() {
    getVertx().sharedData().getCounter("foo").onComplete(onSuccess(counter -> {
      counter.getAndAdd(2).onComplete(onSuccess(res -> {
        assertEquals(0L, res.longValue());
        getVertx().sharedData().getCounter("foo").onComplete(onSuccess(counter2 -> {
          counter2.getAndAdd(2L).onComplete(onSuccess(res2 -> {
            assertEquals(2L, res2.longValue());
            counter2.get().onComplete(onSuccess(res3 -> {
              assertEquals(4L, res3.longValue());
              testComplete();
            }));
          }));
        }));
      }));
    }));
    await();
  }

  @Test
  public void testCompareAndSet() {
    getVertx().sharedData().getCounter("foo").onComplete(onSuccess(counter -> {
      counter.compareAndSet(0L, 2L).onComplete(onSuccess(result -> {
        getVertx().sharedData().getCounter("foo").onComplete(onSuccess(counter2 -> {
          counter2.compareAndSet(2L, 4L).onComplete(onSuccess(result2 -> {
            assertTrue(result2);
            counter2.compareAndSet(3L, 5L).onComplete(onSuccess(result3 -> {
              assertFalse(result3);
              testComplete();
            }));
          }));
        }));
      }));
    }));
    await();
  }

  @Test
  public void testDifferentCounters() {
    getVertx().sharedData().getCounter("foo").onComplete(onSuccess(counter -> {
      counter.incrementAndGet().onComplete(onSuccess(res -> {
        assertEquals(1L, res.longValue());
        getVertx().sharedData().getCounter("bar").onComplete(onSuccess(counter2 -> {
          counter2.incrementAndGet().onComplete(onSuccess(res2 -> {
            assertEquals(1L, res2.longValue());
            counter.incrementAndGet().onComplete(onSuccess(res3 -> {
              assertEquals(2L, res3.longValue());
              testComplete();
            }));
          }));
        }));
      }));
    }));
    await();
  }

}
