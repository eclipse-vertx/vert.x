/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.virtualthread;

import io.vertx.core.internal.ContextInternal;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import static io.vertx.core.internal.ContextInternal.EXECUTE_BLOCKING_ORDERED;
import static io.vertx.core.internal.ContextInternal.EXECUTE_BLOCKING_PREFER_VIRTUAL_THREAD;

public class ExecuteBlockingTest extends VertxTestBase {

  @Test
  public void testExecuteBlockingPreferVirtualThread() {
    vertx.runOnContext(v -> {
      ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
      ctx.executeBlocking(() -> {
        assertTrue(Thread.currentThread().isVirtual());
        return "vt-result";
      }, EXECUTE_BLOCKING_PREFER_VIRTUAL_THREAD).onComplete(onSuccess(res -> {
        assertEquals("vt-result", res);
        testComplete();
      }));
    });
    await();
  }

  @Test
  public void testExecuteBlockingOrderedAndPreferVirtualThread() throws Exception {
    int count = 5;
    CountDownLatch latch = new CountDownLatch(count);
    List<Integer> order = new CopyOnWriteArrayList<>();

    vertx.runOnContext(v -> {
      ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
      int composedFlags = EXECUTE_BLOCKING_PREFER_VIRTUAL_THREAD | EXECUTE_BLOCKING_ORDERED;
      for (int i = 0; i < count; i++) {
        final int idx = i;
        ctx.executeBlocking(() -> {
          assertTrue(Thread.currentThread().isVirtual());
          Thread.sleep(10);
          return idx;
        }, composedFlags).onComplete(onSuccess(res -> {
          order.add(res);
          latch.countDown();
        }));
      }
    });

    awaitLatch(latch);
    for (int i = 0; i < count; i++) {
      assertEquals(i, (int) order.get(i));
    }
  }
}
