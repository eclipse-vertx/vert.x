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
package io.vertx.core.impl;

import io.vertx.core.Promise;
import io.vertx.core.VertxTest;
import io.vertx.test.core.AsyncTestBase;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class CloseFutureTest extends AsyncTestBase {

  @Test
  public void testHookCompletion() {
    CloseFuture cf = new CloseFuture();
    AtomicReference<Promise<Void>> ref = new AtomicReference<>();
    cf.add(completion -> ref.set(completion));
    assertFalse(cf.close().succeeded());
    assertNotNull(ref.get());
    ref.get().complete();
    assertTrue(cf.close().succeeded());
  }

  @Test
  public void testRemoveDisposedCloseFutureHook() {
    CloseFuture cf = new CloseFuture();
    CloseFuture hook = new CloseFuture();
    cf.add(hook);
    assertTrue(hook.close().succeeded());
    cf.close();
    assertFalse(cf.remove(hook));
  }

  @Test
  public void testCloseFutureDuplicateClose() {
    AtomicReference<Promise<Void>> ref = new AtomicReference<>();
    CloseFuture fut = new CloseFuture();
    fut.add(ref::set);
    Promise<Void> p1 = Promise.promise();
    fut.close(p1);
    assertNotNull(ref.get());
    Promise<Void> p2 = Promise.promise();
    fut.close(p2);
    assertFalse(p1.future().isComplete());
    assertFalse(p2.future().isComplete());
    ref.get().complete();
    assertTrue(p1.future().isComplete());
    assertTrue(p2.future().isComplete());
  }

  @Test
  public void testFinalize() {
    CloseFuture cf = new CloseFuture();
    CloseFuture hook = new CloseFuture();
    AtomicInteger closed = new AtomicInteger();
    hook.add(completion -> {
      closed.incrementAndGet();
      completion.complete();
    });
    cf.add(hook);
    hook = null;
    VertxTest.runGC();
    assertTrue(cf.close().succeeded());
  }

  @Test
  public void testCloseHook() {
    CloseFuture cf = new CloseFuture();
    CloseFuture hook = new CloseFuture();
    cf.add(hook);
    hook.close();
    assertFalse(cf.remove(hook));
    assertTrue(cf.close().succeeded());
  }
}
