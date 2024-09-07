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

package io.vertx.tests.future;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.internal.ContextInternal;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FutureAwaitTest extends VertxTestBase {

  @Test
  public void testAwaitFromEventLoopThread() {
    Promise<String> promise = Promise.promise();
    Future<String> future = promise.future();
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    ctx.nettyEventLoop().execute(() -> {
      try {
        future.await();
      } catch (IllegalStateException expected) {
        testComplete();
      }
    });
    await();
  }

  @Test
  public void testAwaitFromNonVertxThread() {
    Promise<String> promise = Promise.promise();
    Future<String> future = promise.future();
    Thread current = Thread.currentThread();
    new Thread(() -> {
      while (current.getState() != Thread.State.WAITING) {
        try {
          Thread.sleep(10);
        } catch (InterruptedException ignore) {
        }
      }
      promise.complete("the-result");
    }).start();
    String res = future.await();
    assertEquals("the-result", res);
  }

  @Test
  public void testAwaitWithTimeout() {
    Promise<String> promise = Promise.promise();
    Future<String> future = promise.future();
    long now = System.currentTimeMillis();
    try {
      future.await(100, TimeUnit.MILLISECONDS);
      fail();
    } catch (TimeoutException expected) {
    }
    assertTrue((System.currentTimeMillis() - now) >= 100);
  }
}
