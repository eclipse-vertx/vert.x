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
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.EventExecutor;
import io.vertx.core.internal.VertxBootstrap;
import io.vertx.test.core.AsyncTestBase;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;

public class EventExecutorProviderTest extends AsyncTestBase {

  @Test
  public void testExecuteTasks() {
    Deque<Runnable> toRun = new ConcurrentLinkedDeque<>();
    VertxBootstrap bootstrap = VertxBootstrap.create();
    bootstrap.eventExecutorProvider(thread -> toRun::add);
    bootstrap.init();
    Vertx vertx = bootstrap.vertx();
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    assertEquals(ThreadingModel.OTHER, ctx.threadingModel());
    assertEquals(0, toRun.size());
    int[] cnt = new int[1];
    ctx.runOnContext(v -> {
      assertTrue(ctx.inThread());
      assertSame(ctx, Vertx.currentContext());
      assertSame(ctx, vertx.getOrCreateContext());
      cnt[0]++;
    });
    assertEquals(1, toRun.size());
    toRun.pop().run();
    assertEquals(1, cnt[0]);
    assertNull(Vertx.currentContext());
  }

  @Test
  public void testEventExecutorReturnsNull() {
    VertxBootstrap bootstrap = VertxBootstrap.create();
    bootstrap.eventExecutorProvider(thread -> null);
    bootstrap.init();
    Vertx vertx = bootstrap.vertx();
    Context ctx = vertx.getOrCreateContext();
    assertEquals(ThreadingModel.EVENT_LOOP, ctx.threadingModel());
  }
}
