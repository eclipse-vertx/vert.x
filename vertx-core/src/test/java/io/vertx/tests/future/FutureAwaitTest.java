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

import io.vertx.core.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.test.core.Checkpoint;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase2;
import org.junit.After;
import org.junit.Test;

import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FutureAwaitTest extends VertxTestBase2 {

  private static final Future<String> SUCCEEDED_FUTURE = Future.succeededFuture("foo");
  private static final Future<String> FAILED_FUTURE = Future.failedFuture(new Exception());

  private ContextInternal context;

  @After
  public void tearDown() throws Exception {
    if (context != null) {
      context.close().await();
    }
  }

  private static ContextInternal createEventLoopContext(Vertx vertx) {
    VertxInternal vertxInternal = (VertxInternal) vertx;
    return vertxInternal.createEventLoopContext();
  }

  private static ContextInternal createWorkerContext(Vertx vertx) {
    VertxInternal vertxInternal = (VertxInternal) vertx;
    return vertxInternal.createWorkerContext();
  }

  private static Runnable runTaskOnContext(Runnable task, ContextInternal ctx) {
    return () -> ctx.runOnContext(v -> task.run());
  }

  private static Runnable runTaskOnNettyEventLoop(Runnable task, ContextInternal ctx) {
    return () -> ctx.nettyEventLoop().execute(task);
  }

  private static Runnable runTaskOnNonVertxThread(Runnable task) {
    return () -> {
      Thread t = new Thread(task);
      t.start();
    };
  }

  private static void testAwaitBehavior(Future<String> future, Function<Runnable, Runnable> executorSetup, Handler<AsyncResult<String>> checks, Checkpoint checkpoint) {
    Runnable testLogic = () -> {
      String res;
      Exception failure;
      try {
        res = future.await();
        failure = null;
      } catch (Exception e) {
        res = null;
        failure = e;
      }
      checks.handle(res != null ? Future.succeededFuture(res) : Future.failedFuture(failure));
      checkpoint.succeed();
    };
    executorSetup.apply(testLogic).run();
  }

  private static Handler<AsyncResult<String>> assertSuccess() {
    return ar -> {
      assertEquals("Expected Future.await() to return the expected result", SUCCEEDED_FUTURE.result(), ar.result());
    };
  }

  private static Handler<AsyncResult<String>> assertFailure() {
    return ar -> {
      assertSame("Expected Future.await() to throw the expected failure", FAILED_FUTURE.cause(), ar.cause());
    };
  }

  private static Handler<AsyncResult<String>> assertIllegal() {
    return ar -> {
      assertTrue("Expected Future.await() to throw IllegalStateException", ar.cause() instanceof IllegalStateException);
    };
  }

  @Test
  public void testSucceededFutureAwaitOnEventLoopContext(Checkpoint checkpoint) {
    context = createEventLoopContext(vertx);
    testAwaitBehavior(SUCCEEDED_FUTURE, task -> runTaskOnContext(task, context), assertSuccess(), checkpoint);
  }

  @Test
  public void testSucceededFutureAwaitOnNettyEventLoop(Checkpoint checkpoint) {
    context = createEventLoopContext(vertx);
    testAwaitBehavior(SUCCEEDED_FUTURE, task -> runTaskOnNettyEventLoop(task, context), assertSuccess(), checkpoint);
  }

  @Test
  public void testSucceededFutureAwaitOnWorkerContext(Checkpoint checkpoint) {
    context = createWorkerContext(vertx);
    testAwaitBehavior(SUCCEEDED_FUTURE, task -> runTaskOnContext(task, context), assertSuccess(), checkpoint);
  }

  @Test
  public void testSucceededFutureAwaitOnNonVertxThread(Checkpoint checkpoint) {
    testAwaitBehavior(SUCCEEDED_FUTURE, task -> runTaskOnNonVertxThread(task), assertSuccess(), checkpoint);
  }

  @Test
  public void testFailedFutureAwaitOnEventLoopContext(Checkpoint checkpoint) {
    context = createEventLoopContext(vertx);
    testAwaitBehavior(FAILED_FUTURE, task -> runTaskOnContext(task, context), assertFailure(), checkpoint);
  }

  @Test
  public void testFailedFutureAwaitOnNettyEventLoop(Checkpoint checkpoint) {
    context = createEventLoopContext(vertx);
    testAwaitBehavior(FAILED_FUTURE, task -> runTaskOnNettyEventLoop(task, context), assertFailure(), checkpoint);
  }

  @Test
  public void testFailedFutureAwaitOnWorkerContext(Checkpoint checkpoint) {
    context = createWorkerContext(vertx);
    testAwaitBehavior(FAILED_FUTURE, task -> runTaskOnContext(task, context), assertFailure(), checkpoint);
  }

  @Test
  public void testFailedFutureAwaitOnNonVertxThread(Checkpoint checkpoint) {
    testAwaitBehavior(FAILED_FUTURE, task -> runTaskOnNonVertxThread(task), assertFailure(), checkpoint);
  }

  @Test
  public void testFutureAwaitOnEventLoopContext(Checkpoint checkpoint) {
    Promise<String> promise = Promise.promise();
    context = createEventLoopContext(vertx);
    testAwaitBehavior(promise.future(), task -> runTaskOnContext(task, context), assertIllegal(), checkpoint);
  }

  @Test
  public void testFutureAwaitOnNettyEventLoop(Checkpoint checkpoint) {
    Promise<String> promise = Promise.promise();
    context = createEventLoopContext(vertx);
    testAwaitBehavior(promise.future(), task -> runTaskOnNettyEventLoop(task, context), assertIllegal(), checkpoint);
  }

  @Test
  public void testFutureAwaitOnWorkerContext(Checkpoint checkpoint) {
    Promise<String> promise = Promise.promise();
    context = createWorkerContext(vertx);
    testAwaitBehavior(promise.future(), task -> runTaskOnContext(task, context), assertIllegal(), checkpoint);
  }

  @Test
  public void testEventuallySucceededFutureAwaitOnNonVertxThread(Checkpoint checkpoint) throws Exception {
    Promise<String> promise = Promise.promise();
    testAwaitBehavior(promise.future(), task -> runTaskOnNonVertxThread(task), assertSuccess(), checkpoint);
    MILLISECONDS.sleep(500);
    SUCCEEDED_FUTURE.onComplete(promise);
  }

  @Test
  public void testEventuallyFailedFutureAwaitOnNonVertxThread(Checkpoint checkpoint) throws Exception {
    Promise<String> promise = Promise.promise();
    testAwaitBehavior(promise.future(), task -> runTaskOnNonVertxThread(task), assertFailure(), checkpoint);
    MILLISECONDS.sleep(500);
    FAILED_FUTURE.onComplete(promise);
  }

  @Test
  public void testAwaitWithTimeout() {
    Promise<String> promise = Promise.promise();
    Future<String> future = promise.future();
    long now = System.currentTimeMillis();
    try {
      future.await(100, MILLISECONDS);
      fail();
    } catch (TimeoutException expected) {
    }
    assertThat(System.currentTimeMillis() - now).isGreaterThanOrEqualTo(100);
  }

  @Test
  public void testAwaitNoStackTraceFailure() {
    String msg = TestUtils.randomAlphaString(10);
    Future<String> future = Future.failedFuture(msg);
    try {
      future.await();
      fail();
    } catch (Exception expected) {
      assertThat(msg).isSameAs(expected.getMessage());
    }
  }
}
