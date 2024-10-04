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
package io.vertx.tests.context;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.impl.VertxThread;
import io.vertx.core.impl.WorkerPool;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class ContextTaskTest extends VertxTestBase {

  enum Op {
    SCHEDULE() {
      @Override
      void exec(ContextInternal ctx, Handler<Void> task) {
        ctx.execute(task);
      }
    }, DISPATCH() {
      @Override
      void exec(ContextInternal ctx, Handler<Void> task) {
        ctx.emit(task);
      }
    };

    abstract void exec(ContextInternal ctx, Handler<Void> task);
  }

  private ExecutorService workerExecutor;

  @Override
  public void setUp() throws Exception {
    workerExecutor = Executors.newFixedThreadPool(2, r -> new VertxThread(r, "vert.x-worker-thread", true, 10, TimeUnit.SECONDS));
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    workerExecutor.shutdown();
    super.tearDown();
  }

  private ContextInternal createEventLoopContext() {
    return ((VertxInternal) vertx).createEventLoopContext();
  }

  private ContextInternal createWorkerContext() {
    return ((VertxInternal) vertx).createWorkerContext(null, null, new WorkerPool(workerExecutor, null), Thread.currentThread().getContextClassLoader());
  }

  // SCHEDULE + DISPATCH

  @Test
  public void testEventLoopDispatchFromSameContext() {
    testOpFromSameContext(Op.DISPATCH, this::createEventLoopContext);
  }

  @Test
  public void testEventLoopScheduleFromSameContext() {
    testOpFromSameContext(Op.SCHEDULE, this::createEventLoopContext);
  }

  @Test
  public void testWorkerDispatchFromSameContext() {
    testOpFromSameContext(Op.DISPATCH, this::createWorkerContext);
  }

  @Test
  public void testWorkerScheduleFromSameContext() {
    testOpFromSameContext(Op.SCHEDULE, this::createWorkerContext);
  }

  private void testOpFromSameContext(Op op, Supplier<ContextInternal> contextSupplier) {
    waitFor(2);
    ContextInternal ctx = contextSupplier.get();
    ctx.runOnContext(v1 -> {
      Thread thread = Thread.currentThread();
      AtomicBoolean flag = new AtomicBoolean(true);
      op.exec(ctx, v2 -> {
        assertSame(ctx, Vertx.currentContext());
        assertSame(thread, Thread.currentThread());
        assertTrue(flag.get());
        complete();
      });
      flag.set(false);
      complete();
    });
    await();
  }

  @Test
  public void testEventLoopDispatchFromSameEventLoop() {
    testOpFromSameEventLoop(Op.DISPATCH, this::createEventLoopContext);
  }

  @Test
  public void testEventLoopScheduleFromSameEventLoop() {
    testOpFromSameEventLoop(Op.SCHEDULE, this::createEventLoopContext);
  }

  @Test
  public void testWorkerDispatchFromSameEventLoop() {
    testOpFromSameEventLoop(Op.DISPATCH, this::createWorkerContext);
  }

  @Test
  public void testWorkerScheduleFromSameEventLoop() {
    testOpFromSameEventLoop(Op.SCHEDULE, this::createWorkerContext);
  }

  @Test
  public void testWorkerEmitFromSameEventLoop() {
    // Invalid case
  }

  private void testOpFromSameEventLoop(Op op, Supplier<ContextInternal> contextSupplier) {
    waitFor(2);
    ContextInternal ctx = contextSupplier.get();
    ctx.nettyEventLoop().execute(() -> {
      assertNull(Vertx.currentContext());
      AtomicBoolean flag = new AtomicBoolean(true);
      op.exec(ctx, v2 -> {
        if (op == Op.SCHEDULE) {
          assertNull(Vertx.currentContext());
        } else {
          assertSame(ctx, Vertx.currentContext());
        }
        if (ctx.isEventLoopContext()) {
          assertTrue(flag.get());
        } else {
          waitUntil(() -> !flag.get());
        }
        complete();
      });
      flag.set(false);
      complete();
    });
    await();
  }

  @Test
  public void testEventLoopDispatchFromAnotherEventLoop() {
    testOpFromAnotherEventLoop(ContextInternal::emit, this::createEventLoopContext, false);
  }

  @Test
  public void testEventLoopScheduleFromAnotherEventLoop() {
    testOpFromAnotherEventLoop(ContextInternal::execute, this::createEventLoopContext,true);
  }

  @Test
  public void testWorkerDispatchFromAnotherEventLoop() {
    testOpFromAnotherEventLoop(ContextInternal::emit, this::createWorkerContext, false);
  }

  @Test
  public void testWorkerScheduleFromAnotherEventLoop() {
    testOpFromAnotherEventLoop(ContextInternal::execute, this::createWorkerContext,true);
  }

  private void testOpFromAnotherEventLoop(BiConsumer<ContextInternal, Handler<Void>> op, Supplier<ContextInternal> contextSupplier, boolean isSchedule) {
    waitFor(2);
    ContextInternal ctx = contextSupplier.get();
    createEventLoopContext().nettyEventLoop().execute(() -> {
      op.accept(ctx, v2 -> {
        if (isSchedule) {
          assertNull(Vertx.currentContext());
        } else {
          assertSame(ctx, Vertx.currentContext());
        }
        complete();
      });
      complete();
    });
    await();
  }

  @Test
  public void testEventLoopDispatchFromSchedule() {
    testOpFromSameSchedule(Op.DISPATCH, this::createEventLoopContext);
  }

  @Test
  public void testEventLoopScheduleFromSchedule() {
    testOpFromSameSchedule(Op.SCHEDULE, this::createEventLoopContext);
  }

  @Test
  public void testWorkerDispatchFromSchedule() {
    testOpFromSameSchedule(Op.DISPATCH, this::createWorkerContext);
  }

  @Test
  public void testWorkerScheduleFromSchedule() {
    testOpFromSameSchedule(Op.SCHEDULE, this::createWorkerContext);
  }

  private void testOpFromSameSchedule(Op op, Supplier<ContextInternal> contextSupplier) {
    waitFor(2);
    ContextInternal ctx = contextSupplier.get();
    ctx.execute(v1 -> {
      Thread thread = Thread.currentThread();
      AtomicBoolean flag = new AtomicBoolean(true);
      op.exec(ctx, v2 -> {
        if (op == Op.SCHEDULE) {
          assertNull(Vertx.currentContext());
        } else {
          assertSame(ctx, Vertx.currentContext());
        }
        assertSame(thread, Thread.currentThread());
        assertTrue(flag.get());
        complete();
      });
      flag.set(false);
      complete();
    });
    await();
  }

  @Test
  public void testEventLoopDispatchFromAnotherThread() {
    testOpFromAnotherThread(ContextInternal::emit, this::createEventLoopContext, false);
  }

  @Test
  public void testEventLoopScheduleFromAnotherThread() {
    testOpFromAnotherThread(ContextInternal::execute, this::createEventLoopContext, true);
  }

  @Test
  public void testWorkerDispatchFromAnotherThread() {
    testOpFromAnotherThread(ContextInternal::emit, this::createWorkerContext, false);
  }

  @Test
  public void testWorkerScheduleFromAnotherThread() {
    testOpFromAnotherThread(ContextInternal::execute, this::createWorkerContext, true);
  }

  private void testOpFromAnotherThread(BiConsumer<ContextInternal, Handler<Void>> op, Supplier<ContextInternal> contextSupplier, boolean expectNullContext) {
    waitFor(1);
    ContextInternal ctx = contextSupplier.get();
    Thread current = Thread.currentThread();
    op.accept(ctx, v2 -> {
      if (expectNullContext) {
        assertNull(Vertx.currentContext());
      } else {
        assertSame(ctx, Vertx.currentContext());
      }
      assertNotSame(current, Thread.currentThread());
      complete();
    });
    await();
  }

  // EMIT

  // TODO : emit!
  // TODO : remove dispatchFromIO

}


