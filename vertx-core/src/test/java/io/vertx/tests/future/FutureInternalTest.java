/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tests.future;

import io.vertx.core.Completable;
import io.vertx.core.Promise;
import io.vertx.core.impl.future.FutureImpl;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FutureInternalTest extends FutureTestBase {

  @Test
  public void testAddListener() {
    FutureImpl<Void> future = (FutureImpl<Void>) Promise.promise();
    AtomicInteger successes = new AtomicInteger();
    AtomicInteger failures = new AtomicInteger();
    Completable<Void> listener = (value, err) -> (err == null ? successes : failures).incrementAndGet();
    future.addListener(listener);
    future.tryComplete(null);
    assertEquals(1, successes.get());
    assertEquals(0, failures.get());
  }

  @Test
  public void testRemoveListener1() {
    testRemoveListener((FutureImpl<Void>) Promise.promise());
  }

  @Test
  public void testRemoveListener2() {
    FutureImpl<Void> fut = (FutureImpl<Void>) Promise.promise();
    fut.onComplete(ar -> {});
    testRemoveListener(fut);
  }

  private void testRemoveListener(FutureImpl<Void> future) {
    AtomicInteger count = new AtomicInteger();
    Completable<Void> listener = (value, err) -> count.incrementAndGet();
    future.addListener(listener);
    future.removeListener(listener);
    future.tryComplete(null);
    assertEquals(0, count.get());
  }
}
