/*
 * Copyright 2015 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.test.core.instrumentation;

import org.junit.Test;

public class SharedDataInstrumentationTest extends InstrumentationTestBase {

  @Test
  public void testClusteredAsyncMap() {
    // TODO
  }

  @Test
  public void testAsyncMap() {
    waitFor(14);
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    vertx.sharedData().<String, String>getAsyncMap("the-map", onSuccess(map -> {
      assertSame(cont, instrumentation.current());
      map.size(onSuccess(size -> {
        assertSame(cont, instrumentation.current());
        complete();
      }));
      map.put("key-1", "some-value", onSuccess(v -> {
        assertSame(cont, instrumentation.current());
        complete();
      }));
      map.put("key-2", "some-value", 10, onSuccess(v -> {
        assertSame(cont, instrumentation.current());
        complete();
      }));
      map.putIfAbsent("key-3", "some-value", onSuccess(v -> {
        assertSame(cont, instrumentation.current());
        complete();
      }));
      map.putIfAbsent("key-4", "some-value", 10, onSuccess(v -> {
        assertSame(cont, instrumentation.current());
        complete();
      }));
      map.get("key-5", onSuccess(val -> {
        assertSame(cont, instrumentation.current());
        complete();
      }));
      map.remove("key-6", onSuccess(prev -> {
        assertSame(cont, instrumentation.current());
        complete();
      }));
      map.removeIfPresent("key-7", "some-value", onSuccess(prev -> {
        assertSame(cont, instrumentation.current());
        complete();
      }));
      map.replace("key-8", "some-value", onSuccess(entries -> {
        assertSame(cont, instrumentation.current());
        complete();
      }));
      map.replaceIfPresent("key-9", "some-value", "another-value", onSuccess(entries -> {
        assertSame(cont, instrumentation.current());
        complete();
      }));
      map.clear(onSuccess(v -> {
        assertSame(cont, instrumentation.current());
        complete();
      }));
      map.entries(onSuccess(entries -> {
        assertSame(cont, instrumentation.current());
        complete();
      }));
      map.values(onSuccess(values -> {
        assertSame(cont, instrumentation.current());
        complete();
      }));
      map.entries(onSuccess(entries -> {
        assertSame(cont, instrumentation.current());
        complete();
      }));
    }));
    cont.suspend();
    await();
  }

  @Test
  public void testCounter() {
    waitFor(8);
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    vertx.sharedData().getCounter("the-counter", onSuccess(counter -> {
      assertSame(cont, instrumentation.current());
      counter.get(onSuccess(val -> {
        assertSame(cont, instrumentation.current());
        complete();
      }));
      counter.compareAndSet(0, 1, onSuccess(val -> {
        assertSame(cont, instrumentation.current());
        complete();
      }));
      counter.incrementAndGet(onSuccess(val -> {
        assertSame(cont, instrumentation.current());
        complete();
      }));
      counter.getAndIncrement(onSuccess(val -> {
        assertSame(cont, instrumentation.current());
        complete();
      }));
      counter.decrementAndGet(onSuccess(val -> {
        assertSame(cont, instrumentation.current());
        complete();
      }));
      counter.decrementAndGet(onSuccess(val -> {
        assertSame(cont, instrumentation.current());
        complete();
      }));
      counter.addAndGet(1, onSuccess(val -> {
        assertSame(cont, instrumentation.current());
        complete();
      }));
      counter.getAndAdd(1, onSuccess(val -> {
        assertSame(cont, instrumentation.current());
        complete();
      }));
    }));
    await();
  }

  @Test
  public void testLock() {
    waitFor(2);
    TestContinuation cont = instrumentation.continuation();
    cont.resume();
    vertx.sharedData().getLock("lock-1", onSuccess(lock -> {
      assertSame(cont, instrumentation.current());
      complete();
    }));
    vertx.sharedData().getLockWithTimeout("lock-2", 10, onSuccess(lock -> {
      assertSame(cont, instrumentation.current());
      complete();
    }));
    await();
  }


}
