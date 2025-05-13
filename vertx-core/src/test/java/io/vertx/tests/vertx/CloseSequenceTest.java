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
package io.vertx.tests.vertx;

import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.CloseSequence;
import io.vertx.test.core.AsyncTestBase;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

public class CloseSequenceTest extends AsyncTestBase {

  private AtomicReference<Completable<Void>> ref1;
  private AtomicReference<Completable<Void>> ref2;
  private AtomicReference<Completable<Void>> ref3;
  private CloseSequence seq;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    ref1 = new AtomicReference<>();
    ref2 = new AtomicReference<>();
    ref3 = new AtomicReference<>();
    seq = new CloseSequence(newValue -> ref3.set(newValue), newValue1 -> ref2.set(newValue1), newValue2 -> ref1.set(newValue2));
  }

  @Test
  public void testCompletion() {
    Future<Void> f1 = seq.progressTo(2);
    assertFalse(f1.isComplete());
    assertNotNull(ref1.get());
    assertNull(ref2.get());
    assertNull(ref3.get());
    ref1.get().succeed();
    assertTrue(f1.succeeded());
    assertNotNull(ref1.get());
    assertNull(ref2.get());
    assertNull(ref3.get());
    Future<Void> f2 = seq.progressTo(1);
    assertFalse(f2.isComplete());
    assertNotNull(ref1.get());
    assertNotNull(ref2.get());
    assertNull(ref3.get());
    ref2.get().succeed();
    assertTrue(f2.succeeded());
    assertNotNull(ref1.get());
    assertNotNull(ref2.get());
    assertNull(ref3.get());
    Future<Void> f3 = seq.progressTo(0);
    assertFalse(f3.isComplete());
    assertNotNull(ref1.get());
    assertNotNull(ref2.get());
    assertNotNull(ref3.get());
    ref3.get().succeed();
    assertTrue(f3.succeeded());
    assertNotNull(ref1.get());
    assertNotNull(ref2.get());
    assertNotNull(ref3.get());
  }

  @Test
  public void testCompletion2() {
    Future<Void> fut2 = seq.progressTo(1);
    assertFalse(fut2.isComplete());
    assertNotNull(ref1.get());
    assertNull(ref2.get());
    assertNull(ref3.get());
    ref1.get().succeed();
    assertFalse(fut2.isComplete());
    assertNotNull(ref1.get());
    assertNotNull(ref2.get());
    assertNull(ref3.get());
    ref2.get().succeed();
    assertTrue(fut2.isComplete());
    assertNotNull(ref1.get());
    assertNotNull(ref2.get());
    assertNull(ref3.get());
  }

  @Test
  public void testConcurrent() {
    Future<Void> fut1 = seq.progressTo(2);
    Future<Void> fut2 = seq.progressTo(1);
    assertFalse(fut1.isComplete());
    assertFalse(fut2.isComplete());
    assertNotNull(ref1.get());
    assertNull(ref2.get());
    assertNull(ref3.get());
    ref1.get().succeed();
    assertTrue(fut1.isComplete());
    assertFalse(fut2.isComplete());
    assertNotNull(ref1.get());
    assertNotNull(ref2.get());
    assertNull(ref3.get());
    ref2.get().succeed();
    assertTrue(fut1.isComplete());
    assertTrue(fut2.isComplete());
    assertNotNull(ref1.get());
    assertNotNull(ref2.get());
    assertNull(ref3.get());
  }

  @Test
  public void testDetachFromCloseFutureOnCompletion() {
    CloseFuture closeFuture = new CloseFuture();
    closeFuture.add(seq);
    seq.close();
    ref1.get().succeed();
    ref2.get().succeed();
    ref3.get().succeed();
    assertFalse(closeFuture.remove(seq));
  }
}
