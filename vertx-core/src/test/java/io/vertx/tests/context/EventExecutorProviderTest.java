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
package io.vertx.tests.context;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxBootstrap;
import io.vertx.test.core.AsyncTestBase;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class EventExecutorProviderTest extends AsyncTestBase {

  @Test
  public void testExecuteTasks() throws Exception {
    ArrayBlockingQueue<Runnable> toRun = new ArrayBlockingQueue<>(50);
    VertxBootstrap bootstrap = VertxBootstrap.create();
    bootstrap.eventExecutorProvider(thread -> toRun::add);
    bootstrap.init();
    Vertx vertx = bootstrap.vertx();
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    assertEquals(ThreadingModel.EXTERNAL, ctx.threadingModel());
    assertEquals(0, toRun.size());
    int[] cnt = new int[1];
    ctx.runOnContext(v -> {
      assertTrue(ctx.inThread());
      assertSame(ctx, Vertx.currentContext());
      assertSame(ctx, vertx.getOrCreateContext());
      cnt[0]++;
    });
    assertEquals(1, toRun.size());
    toRun.take().run();
    assertEquals(1, cnt[0]);
    assertNull(Vertx.currentContext());
    Future<Void> fut = vertx.close();
    while(!fut.succeeded()) {
      Runnable task = toRun.poll(10, TimeUnit.MILLISECONDS);
      if (task != null) {
        task.run();
      }
    }
  }

  @Test
  public void testEventExecutorReturnsNull() {
    VertxBootstrap bootstrap = VertxBootstrap.create();
    bootstrap.eventExecutorProvider(thread -> null);
    bootstrap.init();
    Vertx vertx = bootstrap.vertx();
    try {
      Context ctx = vertx.getOrCreateContext();
      assertEquals(ThreadingModel.EVENT_LOOP, ctx.threadingModel());
    } finally {
      vertx.close().await();
    }
  }
}
