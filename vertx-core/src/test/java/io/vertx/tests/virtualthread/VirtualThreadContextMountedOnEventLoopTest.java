/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.virtualthread;

import io.vertx.core.ThreadingModel;
import io.vertx.core.internal.ContextInternal;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

public class VirtualThreadContextMountedOnEventLoopTest extends VirtualThreadContextTestBase {

  protected ContextInternal createVirtualThreadContext() {
    return vertx.createContext(ThreadingModel.VIRTUAL_THREAD_MOUNTED_ON_EVENT_LOOP);
  }

  @Test
  @Ignore("Need to define behavior for this")
  @Override
  public void testSubmitAfterClose() {
    super.testSubmitAfterClose();
  }

  @Test
  @Ignore("Need to define behavior for this")
  @Override
  public void testAwaitWhenClosed() throws Exception {
    super.testAwaitWhenClosed();
  }

  @Test
  public void testScheduling() throws Exception {
    Assume.assumeTrue(isVirtualThreadAvailable());
    ContextInternal ctx = createVirtualThreadContext();
    int numTasks = 100;
    CountDownLatch latch = new CountDownLatch(numTasks);
    CyclicBarrier barrier = new CyclicBarrier(numTasks);
    for (int i = 0;i < numTasks;i++) {
      ctx.runOnContext(v -> {
        try {
          barrier.await();
        } catch (Exception e) {
          fail(e);
        }
        latch.countDown();
      });
    }
    awaitLatch(latch);
  }

  @Test()
  public void testExclusion() throws Exception {
    Assume.assumeTrue(isVirtualThreadAvailable());
    ContextInternal ctx = createVirtualThreadContext();
    int numTasks = 100;
    int numSpins = 1000;
    CountDownLatch latch = new CountDownLatch(numTasks);
    AtomicInteger concurrent = new AtomicInteger();
    for (int i = 0;i < numTasks;i++) {
      int val = i + 1;
      ctx.runOnContext(v -> {
        for (int j = 0;j < 10;j++) {
          assertEquals(0, concurrent.getAndSet(val));
          for (int k = 0;k < numSpins;k++) {
            assertEquals(val, concurrent.get());
          }
          concurrent.set(0);
          try {
            Thread.sleep(1);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        }
        latch.countDown();
      });
    }
    awaitLatch(latch);
  }
}
