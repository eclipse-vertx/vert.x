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

package io.vertx.core;

import io.vertx.test.core.VertxTestBase;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FutureTestBase extends VertxTestBase {

  class Checker<T> {

    private final Future<T> future;
    private final AtomicReference<AsyncResult<T>> result = new AtomicReference<>();
    private final AtomicInteger count = new AtomicInteger();

    Checker(Future<T> future) {
      future.onComplete(ar -> {
        count.incrementAndGet();
        result.set(ar);
      });
      this.future = future;
    }

    void assertNotCompleted() {
      assertFalse(future.isComplete());
      assertFalse(future.succeeded());
      assertFalse(future.failed());
      assertNull(future.cause());
      assertNull(future.result());
      assertEquals(0, count.get());
      assertNull(result.get());
    }

    void assertSucceeded(T expected) {
      assertTrue(future.isComplete());
      assertTrue(future.succeeded());
      assertFalse(future.failed());
      assertNull(future.cause());
      assertEquals(expected, future.result());
      assertEquals(1, count.get());
      AsyncResult<T> ar = result.get();
      assertNotNull(ar);
      assertTrue(ar.succeeded());
      assertFalse(ar.failed());
      assertNull(ar.cause());
      assertEquals(expected, future.result());
    }

    void assertFailed(Throwable expected) {
      assertEquals(expected, assertFailed());
    }

    Throwable assertFailed() {
      assertTrue(future.isComplete());
      assertFalse(future.succeeded());
      assertTrue(future.failed());
      assertEquals(null, future.result());
      assertEquals(1, count.get());
      AsyncResult<T> ar = result.get();
      assertNotNull(ar);
      assertFalse(ar.succeeded());
      assertTrue(ar.failed());
      assertNull(ar.result());
      return future.cause();
    }
  }
}
