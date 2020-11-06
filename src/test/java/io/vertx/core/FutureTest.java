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

package io.vertx.core;

import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.NoStackTraceThrowable;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FutureTest extends VertxTestBase {

  @Test
  public void testCreateWithHandler() {
    AtomicInteger count = new AtomicInteger();
    AtomicReference<Promise<String>> ref = new AtomicReference<>();
    Future<String> f2 = Future.future(p1 -> {
      assertFalse(p1.future().isComplete());
      count.incrementAndGet();
      ref.set(p1);
    });
    assertSame(f2, ref.get().future());
    assertEquals(1, count.get());
    new Checker<>(f2).assertNotCompleted();
    ref.set(null);
    count.set(0);
    f2 = Future.future(f1 -> {
      count.incrementAndGet();
      ref.set(f1);
      f1.complete("the-value");
    });
    assertSame(f2, ref.get().future());
    assertEquals(1, count.get());
    new Checker<>(f2).assertSucceeded("the-value");
    ref.set(null);
    count.set(0);
    RuntimeException cause = new RuntimeException();
    f2 = Future.future(f1 -> {
      count.incrementAndGet();
      ref.set(f1);
      f1.fail(cause);
    });
    assertSame(f2, ref.get().future());
    assertEquals(1, count.get());
    new Checker<>(f2).assertFailed(cause);
    Future f3 = Future.future(f -> {
      throw cause;
    });
    assertSame(cause, f3.cause());
  }

  @Test
  public void testStateAfterCompletion() {
    Object foo = new Object();
    Future<Object> future = Future.succeededFuture(foo);
    assertTrue(future.succeeded());
    assertFalse(future.failed());
    assertTrue(future.isComplete());
    assertEquals(foo, future.result());
    assertNull(future.cause());
    Exception cause = new Exception();
    future = Future.failedFuture(cause);
    assertFalse(future.succeeded());
    assertTrue(future.failed());
    assertTrue(future.isComplete());
    assertNull(future.result());
    assertEquals(cause, future.cause());
  }

  @Test
  public void testCallSetHandlerBeforeCompletion() {
    AtomicBoolean called = new AtomicBoolean();
    Promise<Object> promise = Promise.promise();
    promise.future().onComplete(result -> {
      assertTrue(result.succeeded());
      assertFalse(result.failed());
      assertEquals(null, result.result());
      assertEquals(null, result.cause());
      called.set(true);
    });
    assertFalse(called.get());
    promise.complete(null);
    assertTrue(called.get());
    called.set(false);
    Object foo = new Object();
    promise = Promise.promise();
    promise.future().onComplete(result -> {
      called.set(true);
      assertTrue(result.succeeded());
      assertFalse(result.failed());
      assertEquals(foo, result.result());
      assertEquals(null, result.cause());
    });
    assertFalse(called.get());
    promise.complete(foo);
    assertTrue(called.get());
    called.set(false);
    Exception cause = new Exception();
    promise = Promise.promise();
    promise.future().onComplete(result -> {
      called.set(true);
      assertFalse(result.succeeded());
      assertTrue(result.failed());
      assertEquals(null, result.result());
      assertEquals(cause, result.cause());
    });
    assertFalse(called.get());
    promise.fail(cause);
    assertTrue(called.get());
  }

  @Test
  public void testCallSetHandlerAfterCompletion() {
    AtomicBoolean called = new AtomicBoolean();
    Future<Object> future = Future.succeededFuture();
    future.onComplete(result -> {
      assertTrue(result.succeeded());
      assertFalse(result.failed());
      assertEquals(null, result.result());
      assertEquals(null, result.cause());
      called.set(true);
    });
    assertTrue(called.get());
    called.set(false);
    Object foo = new Object();
    future = Future.succeededFuture(foo);
    future.onComplete(result -> {
      assertTrue(result.succeeded());
      assertFalse(result.failed());
      assertEquals(foo, result.result());
      assertEquals(null, result.cause());
      called.set(true);
    });
    assertTrue(called.get());
    called.set(false);
    Exception cause = new Exception();
    future = Future.failedFuture(cause);
    future.onComplete(result -> {
      assertFalse(result.succeeded());
      assertTrue(result.failed());
      assertEquals(null, result.result());
      assertEquals(cause, result.cause());
      called.set(true);
    });
    assertTrue(called.get());
  }

  @Test
  public void testResolveFutureToHandler() {
    Consumer<Handler<AsyncResult<String>>> consumer = handler -> handler.handle(Future.succeededFuture("the-result"));
    Promise<String> promise = Promise.promise();
    consumer.accept(promise);
    assertTrue(promise.future().isComplete());
    assertTrue(promise.future().succeeded());
    assertEquals("the-result", promise.future().result());
  }

  @Test
  public void testFailFutureToHandler() {
    Throwable cause = new Throwable();
    Consumer<Handler<AsyncResult<String>>> consumer = handler -> {
      handler.handle(Future.failedFuture(cause));
    };
    Promise<String> promise = Promise.promise();
    consumer.accept(promise);
    assertTrue(promise.future().isComplete());
    assertTrue(promise.future().failed());
    assertEquals(cause, promise.future().cause());
  }


  @Test
  public void testCreateFailedWithNullFailure() {
    Future<String> future = Future.failedFuture((Throwable)null);
    Checker<String> checker = new Checker<>(future);
    NoStackTraceThrowable failure = (NoStackTraceThrowable) checker.assertFailed();
    assertNull(failure.getMessage());
  }

  @Test
  public void testFailureFutureWithNullFailure() {
    Promise<String> promise = Promise.promise();
    promise.fail((Throwable)null);
    Checker<String> checker = new Checker<>(promise.future());
    NoStackTraceThrowable failure = (NoStackTraceThrowable) checker.assertFailed();
    assertNull(failure.getMessage());
  }

  @Test
  public void testAllSucceeded() {
    testAllSucceeded(CompositeFuture::all);
  }

  @Test
  public void testAllSucceededWithList() {
    testAllSucceeded((f1, f2) -> CompositeFuture.all(Arrays.asList(f1, f2)));
  }

  private void testAllSucceeded(BiFunction<Future<String>, Future<Integer>, CompositeFuture> all) {
    Promise<String> p1 = Promise.promise();
    Future<String> f1 = p1.future();
    Promise<Integer> p2 = Promise.promise();
    Future<Integer> f2 = p2.future();
    CompositeFuture composite = all.apply(f1, f2);
    Checker<CompositeFuture> checker = new Checker<>(composite);
    checker.assertNotCompleted();
    assertEquals(null, composite.<String>resultAt(0));
    assertEquals(null, composite.<Integer>resultAt(1));
    p1.complete("something");
    checker.assertNotCompleted();
    assertEquals("something", composite.resultAt(0));
    assertEquals(null, composite.<Integer>resultAt(1));
    p2.complete(3);
    checker.assertSucceeded(composite);
    assertEquals("something", composite.resultAt(0));
    assertEquals(3, (int)composite.resultAt(1));
  }

  @Test
  public void testAllWithEmptyList() {
    CompositeFuture composite = CompositeFuture.all(Collections.emptyList());
    assertTrue(composite.isComplete());
  }

  @Test
  public void testAllFailed() {
    testAllFailed(CompositeFuture::all);
  }

  @Test
  public void testAllFailedWithList() {
    testAllFailed((f1, f2) -> CompositeFuture.all(Arrays.asList(f1, f2)));
  }

  private void testAllFailed(BiFunction<Future<String>, Future<Integer>, CompositeFuture> all) {
    Promise<String> p1 = Promise.promise();
    Future<String> f1 = p1.future();
    Promise<Integer> p2 = Promise.promise();
    Future<Integer> f2 = p2.future();
    CompositeFuture composite = all.apply(f1, f2);
    Checker<CompositeFuture> checker = new Checker<>(composite);
    p1.complete("s");
    Exception cause = new Exception();
    p2.fail(cause);
    checker.assertFailed(cause);
    assertEquals("s", composite.resultAt(0));
    assertEquals(null, composite.<Integer>resultAt(1));
  }

  @Test
  public void testAllLargeList() {
    testAllLargeList(63);
    testAllLargeList(64);
    testAllLargeList(65);
    testAllLargeList(100);
  }

  private void testAllLargeList(int size) {
    List<Future> list = new ArrayList<>();
    for (int i = 0;i < size;i++) {
      list.add(Future.succeededFuture());
    }
    CompositeFuture composite = CompositeFuture.all(list);
    Checker<CompositeFuture> checker = new Checker<>(composite);
    checker.assertSucceeded(composite);
    for (int i = 0;i < size;i++) {
      list.clear();
      Throwable cause = new Exception();
      for (int j = 0;j < size;j++) {
        list.add(i == j ? Future.failedFuture(cause) : Future.succeededFuture());
      }
      composite = CompositeFuture.all(list);
      checker = new Checker<>(composite);
      checker.assertFailed(cause);
      for (int j = 0;j < size;j++) {
        if (i == j) {
          assertTrue(composite.failed(j));
        } else {
          assertTrue(composite.succeeded(j));
        }
      }
    }
  }

  @Test
  public void testAnySucceeded1() {
    testAnySucceeded1(CompositeFuture::any);
  }

  @Test
  public void testAnySucceeded1WithList() {
    testAnySucceeded1((f1, f2) -> CompositeFuture.any(Arrays.asList(f1, f2)));
  }

  private void testAnySucceeded1(BiFunction<Future<String>, Future<Integer>, CompositeFuture> any) {
    Promise<String> p1 = Promise.promise();
    Future<String> f1 = p1.future();
    Promise<Integer> p2 = Promise.promise();
    Future<Integer> f2 = p2.future();
    CompositeFuture composite = any.apply(f1, f2);
    Checker<CompositeFuture> checker = new Checker<>(composite);
    checker.assertNotCompleted();
    assertEquals(null, composite.<String>resultAt(0));
    assertEquals(null, composite.<Integer>resultAt(1));
    p1.complete("something");
    checker.assertSucceeded(composite);
    p2.complete(3);
    checker.assertSucceeded(composite);
  }

  @Test
  public void testAnyWithEmptyList() {
    CompositeFuture composite = CompositeFuture.any(Collections.emptyList());
    assertTrue(composite.isComplete());
  }

  @Test
  public void testAnySucceeded2() {
    testAnySucceeded2(CompositeFuture::any);
  }

  @Test
  public void testAnySucceeded2WithList() {
    testAnySucceeded2(CompositeFuture::any);
  }

  private void testAnySucceeded2(BiFunction<Future<String>, Future<Integer>, CompositeFuture> any) {
    Promise<String> p1 = Promise.promise();
    Future<String> f1 = p1.future();
    Promise<Integer> p2 = Promise.promise();
    Future<Integer> f2 = p2.future();
    CompositeFuture composite = any.apply(f1, f2);
    Checker<CompositeFuture> checker = new Checker<>(composite);
    p1.fail("failure");
    checker.assertNotCompleted();
    p2.complete(3);
    checker.assertSucceeded(composite);
  }

  @Test
  public void testAnyFailed() {
    testAnyFailed(CompositeFuture::any);
  }

  @Test
  public void testAnyFailedWithList() {
    testAnyFailed((f1, f2) -> CompositeFuture.any(Arrays.asList(f1, f2)));
  }

  private void testAnyFailed(BiFunction<Future<String>, Future<Integer>, CompositeFuture> any) {
    Promise<String> p1 = Promise.promise();
    Future<String> f1 = p1.future();
    Promise<Integer> p2 = Promise.promise();
    Future<Integer> f2 = p2.future();
    CompositeFuture composite = any.apply(f1, f2);
    Checker<CompositeFuture> checker = new Checker<>(composite);
    p1.fail("failure");
    checker.assertNotCompleted();
    Throwable cause = new Exception();
    p2.fail(cause);
    checker.assertFailed(cause);
  }

  @Test
  public void testAnyLargeList() {
    testAnyLargeList(63);
    testAnyLargeList(64);
    testAnyLargeList(65);
    testAnyLargeList(100);
  }

  private void testAnyLargeList(int size) {
    List<Future> list = new ArrayList<>();
    for (int i = 0;i < size;i++) {
      list.add(Future.failedFuture(new Exception()));
    }
    CompositeFuture composite = CompositeFuture.any(list);
    Checker<CompositeFuture> checker = new Checker<>(composite);
    assertNotNull(checker.assertFailed());
    for (int i = 0;i < size;i++) {
      list.clear();
      for (int j = 0;j < size;j++) {
        list.add(i == j ? Future.succeededFuture() : Future.failedFuture(new RuntimeException()));
      }
      composite = CompositeFuture.any(list);
      checker = new Checker<>(composite);
      checker.assertSucceeded(composite);
      for (int j = 0;j < size;j++) {
        if (i == j) {
          assertTrue(composite.succeeded(j));
        } else {
          assertTrue(composite.failed(j));
        }
      }
    }
  }

  @Test
  public void testJoinSucceeded() {
    testJoinSucceeded(CompositeFuture::join);
  }

  @Test
  public void testJoinSucceededWithList() {
    testJoinSucceeded((f1, f2) -> CompositeFuture.join(Arrays.asList(f1, f2)));
  }

  private void testJoinSucceeded(BiFunction<Future<String>, Future<Integer>, CompositeFuture> join) {
    Promise<String> p1 = Promise.promise();
    Future<String> f1 = p1.future();
    Promise<Integer> p2 = Promise.promise();
    Future<Integer> f2 = p2.future();
    CompositeFuture composite = join.apply(f1, f2);
    Checker<CompositeFuture> checker = new Checker<>(composite);
    checker.assertNotCompleted();
    p1.complete("foo");
    checker.assertNotCompleted();
    p2.complete();
    checker.assertSucceeded(composite);
  }

  @Test
  public void testJoinFailed1() {
    testJoinFailed1(CompositeFuture::join);
  }

  @Test
  public void testJoinFailed1WithList() {
    testJoinFailed1((f1, f2) -> CompositeFuture.join(Arrays.asList(f1, f2)));
  }

  private void testJoinFailed1(BiFunction<Future<String>, Future<Integer>, CompositeFuture> join) {
    Promise<String> p1 = Promise.promise();
    Future<String> f1 = p1.future();
    Promise<Integer> p2 = Promise.promise();
    Future<Integer> f2 = p2.future();
    CompositeFuture composite = join.apply(f1, f2);
    Checker<CompositeFuture> checker = new Checker<>(composite);
    checker.assertNotCompleted();
    p1.complete("foo");
    checker.assertNotCompleted();
    Throwable cause = new Throwable();
    p2.fail(cause);
    assertSame(checker.assertFailed(), cause);
  }

  @Test
  public void testJoinFailed2() {
    testJoinFailed2(CompositeFuture::join);
  }

  @Test
  public void testJoinFailed2WithList() {
    testJoinFailed2((f1, f2) -> CompositeFuture.join(Arrays.asList(f1, f2)));
  }

  private void testJoinFailed2(BiFunction<Future<String>, Future<Integer>, CompositeFuture> join) {
    Promise<String> p1 = Promise.promise();
    Future<String> f1 = p1.future();
    Promise<Integer> p2 = Promise.promise();
    Future<Integer> f2 = p2.future();
    CompositeFuture composite = join.apply(f1, f2);
    Checker<CompositeFuture> checker = new Checker<>(composite);
    checker.assertNotCompleted();
    Throwable cause = new Throwable();
    p1.fail(cause);
    checker.assertNotCompleted();
    p2.complete(10);
    assertSame(cause, checker.assertFailed());
  }

  @Test
  public void testJoinFailed3() {
    testJoinFailed3(CompositeFuture::join);
  }

  @Test
  public void testJoinFailed3WithList() {
    testJoinFailed3((f1, f2) -> CompositeFuture.join(Arrays.asList(f1, f2)));
  }

  private void testJoinFailed3(BiFunction<Future<String>, Future<Integer>, CompositeFuture> join) {
    Promise<String> p1 = Promise.promise();
    Future<String> f1 = p1.future();
    Promise<Integer> p2 = Promise.promise();
    Future<Integer> f2 = p2.future();
    CompositeFuture composite = join.apply(f1, f2);
    Checker<CompositeFuture> checker = new Checker<>(composite);
    checker.assertNotCompleted();
    Throwable cause1 = new Throwable();
    p1.fail(cause1);
    checker.assertNotCompleted();
    Throwable cause2 = new Throwable();
    p2.fail(cause2);
    assertSame(cause1, checker.assertFailed());
  }

  @Test
  public void testJoinWithEmptyList() {
    CompositeFuture composite = CompositeFuture.join(Collections.emptyList());
    assertTrue(composite.isComplete());
  }

  @Test
  public void testCompositeFutureToList() {
    Promise<String> p1 = Promise.promise();
    Future<String> f1 = p1.future();
    Promise<Integer> p2 = Promise.promise();
    Future<Integer> f2 = p2.future();
    CompositeFuture composite = CompositeFuture.all(f1, f2);
    assertEquals(Arrays.asList(null, null), composite.list());
    p1.complete("foo");
    assertEquals(Arrays.asList("foo", null), composite.list());
    p2.complete(4);
    assertEquals(Arrays.asList("foo", 4), composite.list());
  }

  @Test
  public void testCompositeFutureCauses() {
    CompositeFuture composite = CompositeFuture.all(Future.failedFuture("blabla"), Future.succeededFuture());

    assertEquals(2, composite.causes().size());
    assertNotNull(composite.causes().get(0));
    assertEquals("blabla", composite.causes().get(0).getMessage());

    assertNull(composite.causes().get(1));
  }

  @Test
  public void testCompositeFutureMulti() {
    Promise<String> p1 = Promise.promise();
    Future<String> f1 = p1.future();
    Promise<Integer> p2 = Promise.promise();
    Future<Integer> f2 = p2.future();
    CompositeFuture composite = CompositeFuture.all(f1, f2);
    AtomicInteger count = new AtomicInteger();
    composite.onComplete(ar -> {
      count.compareAndSet(0, 1);
    });
    composite.onComplete(ar -> {
      count.compareAndSet(1, 2);
    });
    p1.complete("foo");
    p2.complete(4);
    assertEquals(2, count.get());
  }

  @Test
  public void testComposeSuccessToSuccess() {
    AtomicReference<String> ref = new AtomicReference<>();
    Promise<Integer> p = Promise.promise();
    Future<Integer> c = p.future();
    Promise<String> p3 = Promise.promise();
    Future<String> f3 = p3.future();
    Future<Integer> f4 = f3.compose(string -> {
      ref.set(string);
      return c;
    });
    Checker<Integer>  checker = new Checker<>(f4);
    p3.complete("abcdef");
    checker.assertNotCompleted();
    assertEquals("abcdef", ref.get());
    p.complete(6);
    checker.assertSucceeded(6);
  }

  @Test
  public void testComposeSuccessToFailure() {
    Throwable cause = new Throwable();
    AtomicReference<String> ref = new AtomicReference<>();
    Promise<Integer> p = Promise.promise();
    Future<Integer> c = p.future();
    Promise<String> p3 = Promise.promise();
    Future<String> f3 = p3.future();
    Future<Integer> f4 = f3.compose(string -> {
      ref.set(string);
      return c;
    });
    Checker<Integer> checker = new Checker<>(f4);
    p3.complete("abcdef");
    p.fail(cause);
    checker.assertFailed(cause);
  }

  @Test
  public void testComposeFailure() {
    Exception cause = new Exception();

    Promise<String> p3 = Promise.promise();
    Future<String> f3 = p3.future();
    Future<Integer> f4 = f3.compose(string -> Future.succeededFuture(string.length()));
    Checker<Integer> checker = new Checker<>(f4);
    p3.fail(cause);
    checker.assertFailed(cause);
  }

  @Test
  public void testComposeFails() {
    RuntimeException cause = new RuntimeException();
    Promise<String> p3 = Promise.promise();
    Future<String> f3 = p3.future();
    Future<Integer> f4 = f3.compose(string -> { throw cause; });
    Checker<Integer> checker = new Checker<>(f4);
    p3.complete("foo");
    checker.assertFailed(cause);
  }

  @Test
  public void testComposeWithNullFunction() {
    Promise<Integer> p = Promise.promise();
    Future<Integer> f = p.future();
    try {
      f.compose((Function<Integer, Future<Integer>>) null);
      fail();
    } catch (NullPointerException ignore) {
    }
  }

  @Test
  public void testMapSuccess() {
    Promise<Integer> p = Promise.promise();
    Future<Integer> f = p.future();
    Future<String> mapped = f.map(Object::toString);
    Checker<String> checker = new Checker<>(mapped);
    checker.assertNotCompleted();
    p.complete(3);
    checker.assertSucceeded("3");
  }

  @Test
  public void testMapValueSuccess() {
    Promise<Integer> p = Promise.promise();
    Future<Integer> f = p.future();
    Future<String> mapped = f.map("5");
    Checker<String> checker = new Checker<>(mapped);
    checker.assertNotCompleted();
    p.complete(3);
    checker.assertSucceeded("5");
  }

  @Test
  public void testMapValueAlreadySuccess() {
    Future<Integer> f = Future.succeededFuture(3);
    Future<String> mapped = f.map("5");
    Checker<String> checker = new Checker<>(mapped);
    checker.assertSucceeded("5");
  }

  @Test
  public void testMapFailure() {
    Throwable cause = new Throwable();
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    Future<String> mapped = f.map(Object::toString);
    Checker<String> checker = new Checker<>(mapped);
    checker.assertNotCompleted();
    p.fail(cause);
    checker.assertFailed(cause);
  }

  @Test
  public void testMapAlreadyFailure() {
    Throwable cause = new Throwable();
    Future<String> f = Future.failedFuture(cause);
    Future<String> mapped = f.map(Object::toString);
    Checker<String> checker = new Checker<>(mapped);
    checker.assertFailed(cause);
  }

  @Test
  public void testMapValueFailure() {
    Throwable cause = new Throwable();
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    Future<String> mapped = f.map("5");
    Checker<String> checker = new Checker<>(mapped);
    checker.assertNotCompleted();
    p.fail(cause);
    checker.assertFailed(cause);
  }

  @Test
  public void testMapValueAlreadyFailure() {
    Throwable cause = new Throwable();
    Future<String> f = Future.failedFuture(cause);
    Future<String> mapped = f.map("5");
    Checker<String> checker = new Checker<>(mapped);
    checker.assertFailed(cause);
  }

  @Test
  public void testMapFails() {
    RuntimeException cause = new RuntimeException();
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    Future<Object> mapped = f.map(i -> {
      throw cause;
    });
    Checker<Object> checker = new Checker<>(mapped);
    p.fail(cause);
    checker.assertFailed(cause);
  }

  @Test
  public void testMapWithNullFunction() {
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    try {
      f.map((Function<String, String>) null);
      fail();
    } catch (NullPointerException ignore) {
    }
    try {
      asyncResult(f).map((Function<String, String>) null);
      fail();
    } catch (NullPointerException ignore) {
    }
  }

  @Test
  public void testMapEmpty() {
    Promise<Integer> p = Promise.promise();
    Future<Integer> f = p.future();
    Future<String> mapped = f.mapEmpty();
    Checker<String> checker = new Checker<>(mapped);
    checker.assertNotCompleted();
    p.complete(3);
    checker.assertSucceeded(null);
  }

  @Test
  public void testRecoverSuccessWithSuccess() {
    AtomicBoolean called = new AtomicBoolean();
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    Future<String> r = f.recover(t -> {
      called.set(true);
      throw new AssertionError();
    });
    Checker<String> checker = new Checker<>(r);
    checker.assertNotCompleted();
    p.complete("yeah");
    assertTrue(r.succeeded());
    checker.assertSucceeded("yeah");
    assertFalse(called.get());
  }

  @Test
  public void testRecoverFailureWithSuccess() {
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    Future<String> r = f.recover(t -> Future.succeededFuture(t.getMessage()));
    Checker<String> checker = new Checker<>(r);
    checker.assertNotCompleted();
    p.fail("recovered");
    checker.assertSucceeded("recovered");
  }

  @Test
  public void testRecoverFailureWithFailure() {
    Throwable cause = new Throwable();
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    Future<String> r = f.recover(t -> Future.failedFuture(cause));
    Checker<String> checker = new Checker<>(r);
    checker.assertNotCompleted();
    p.fail("recovered");
    checker.assertFailed(cause);
  }

  @Test
  public void testRecoverFailureFails() {
    RuntimeException cause = new RuntimeException("throw");
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    Future<String> r = f.recover(t -> {
      throw cause;
    });
    Checker<String> checker = new Checker<>(r);
    checker.assertNotCompleted();
    p.fail("recovered");
    checker.assertFailed(cause);
  }

  @Test
  public void testRecoverWithNullFunction() {
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    try {
      f.recover(null);
      fail();
    } catch (NullPointerException ignore) {
    }
  }

  @Test
  public void testOtherwiseSuccessWithSuccess() {
    AtomicBoolean called = new AtomicBoolean();
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    Future<String> r = f.otherwise(t -> {
      called.set(true);
      throw new AssertionError();
    });
    Checker<String> checker = new Checker<>(r);
    checker.assertNotCompleted();
    p.complete("yeah");
    assertTrue(r.succeeded());
    checker.assertSucceeded("yeah");
    assertFalse(called.get());
  }

  @Test
  public void testOtherwiseAlreadySuccessWithSuccess() {
    AtomicBoolean called = new AtomicBoolean();
    Future<String> f = Future.succeededFuture("yeah");
    Future<String> r = f.otherwise(t -> {
      called.set(true);
      throw new AssertionError();
    });
    Checker<String> checker = new Checker<>(r);
    assertTrue(r.succeeded());
    checker.assertSucceeded("yeah");
    assertFalse(called.get());
  }

  @Test
  public void testOtherwiseValueSuccessWithSuccess() {
    AtomicBoolean called = new AtomicBoolean();
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    Future<String> r = f.otherwise("other");
    Checker<String> checker = new Checker<>(r);
    checker.assertNotCompleted();
    p.complete("yeah");
    assertTrue(r.succeeded());
    checker.assertSucceeded("yeah");
    assertFalse(called.get());
  }

  @Test
  public void testOtherwiseValueAlreadySuccessWithSuccess() {
    AtomicBoolean called = new AtomicBoolean();
    Future<String> f = Future.succeededFuture("yeah");
    Future<String> r = f.otherwise("other");
    Checker<String> checker = new Checker<>(r);
    assertTrue(r.succeeded());
    checker.assertSucceeded("yeah");
    assertFalse(called.get());
  }

  @Test
  public void testOtherwiseFailureWithSuccess() {
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    Future<String> r = f.otherwise(Throwable::getMessage);
    Checker<String> checker = new Checker<>(r);
    checker.assertNotCompleted();
    p.fail("recovered");
    checker.assertSucceeded("recovered");
  }

  @Test
  public void testOtherwiseValueFailureWithSuccess() {
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    Future<String> r = f.otherwise("other");
    Checker<String> checker = new Checker<>(r);
    checker.assertNotCompleted();
    p.fail("recovered");
    checker.assertSucceeded("other");
  }

  @Test
  public void testOtherwiseValueAlreadyFailureWithSuccess() {
    Future<String> f = Future.failedFuture("recovered");
    Future<String> r = f.otherwise("other");
    Checker<String> checker = new Checker<>(r);
    checker.assertSucceeded("other");
  }

  @Test
  public void testOtherwiseFails() {
    RuntimeException cause = new RuntimeException("throw");
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    Future<String> r = f.otherwise(t -> {
      throw cause;
    });
    Checker<String> checker = new Checker<>(r);
    checker.assertNotCompleted();
    p.fail("recovered");
    checker.assertFailed(cause);
  }

  @Test
  public void testHandlerFailureWithContext() {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    Promise<String> promise = ctx.promise();
    promise.complete("abc");
    RuntimeException failure = new RuntimeException();
    ctx.exceptionHandler(err -> {
      assertSame(failure, err);
      testComplete();
    });
    promise.future().onComplete(ar -> {
      throw failure;
    });
    await();
  }

  @Test
  public void testHandlerFailureWithoutContext() {
    Promise<String> promise = Promise.promise();
    promise.complete("abc");
    RuntimeException failure = new RuntimeException();
    try {
      promise.future().onComplete(ar -> {
        throw failure;
      });
      fail();
    } catch (Exception e) {
      // This is the expected behavior, without a context we don't have a specific place to report to
      // and we let the exception bubble to the caller so it is not swallowed
      assertSame(failure, e);
    }
  }

  @Test
  public void testDefaultCompleter() {
    AsyncResult<Object> succeededAsyncResult = new AsyncResult<Object>() {
      Object result = new Object();
      public Object result() { return result; }
      public Throwable cause() { throw new UnsupportedOperationException(); }
      public boolean succeeded() { return true; }
      public boolean failed() { throw new UnsupportedOperationException(); }
      public <U> AsyncResult<U> map(Function<Object, U> mapper) { throw new UnsupportedOperationException(); }
      public <V> AsyncResult<V> map(V value) { throw new UnsupportedOperationException(); }
    };

    AsyncResult<Object> failedAsyncResult = new AsyncResult<Object>() {
      Throwable cause = new Throwable();
      public Object result() { throw new UnsupportedOperationException(); }
      public Throwable cause() { return cause; }
      public boolean succeeded() { return false; }
      public boolean failed() { throw new UnsupportedOperationException(); }
      public <U> AsyncResult<U> map(Function<Object, U> mapper) { throw new UnsupportedOperationException(); }
      public <V> AsyncResult<V> map(V value) { throw new UnsupportedOperationException(); }
    };

    class DefaultCompleterTestFuture<T> implements Future<T> {
      boolean succeeded;
      boolean failed;
      T result;
      Throwable cause;
      public boolean isComplete() { throw new UnsupportedOperationException(); }
      public Future<T> onComplete(Handler<AsyncResult<T>> handler) { throw new UnsupportedOperationException(); }

      public void complete(T result) {
        if (!tryComplete(result)) {
          throw new IllegalStateException();
        }
      }
      public void complete() {
        if (!tryComplete()) {
          throw new IllegalStateException();
        }
      }
      public void fail(Throwable cause) {
        if (!tryFail(cause)) {
          throw new IllegalStateException();
        }
      }
      public void fail(String failureMessage) {
        if (!tryFail(failureMessage)) {
          throw new IllegalStateException();
        }
      }
      public boolean tryComplete(T result) {
        if (succeeded || failed) {
          return false;
        }
        succeeded = true;
        this.result = result;
        return true;
      }
      public boolean tryComplete() { throw new UnsupportedOperationException(); }
      public boolean tryFail(Throwable cause) {
        if (succeeded || failed) {
          return false;
        }
        failed = true;
        this.cause = cause;
        return true;
      }
      public boolean tryFail(String failureMessage) { throw new UnsupportedOperationException(); }
      public T result() { throw new UnsupportedOperationException(); }
      public Throwable cause() { throw new UnsupportedOperationException(); }
      public boolean succeeded() { throw new UnsupportedOperationException(); }
      public boolean failed() { throw new UnsupportedOperationException(); }
      public <U> Future<U> compose(Function<T, Future<U>> successMapper, Function<Throwable, Future<U>> failureMapper) { throw new UnsupportedOperationException(); }
      public <U> Future<U> map(Function<T, U> mapper) { throw new UnsupportedOperationException(); }
      public <V> Future<V> map(V value) { throw new UnsupportedOperationException(); }
      public Future<T> otherwise(Function<Throwable, T> mapper) { throw new UnsupportedOperationException(); }
      public Future<T> otherwise(T value) { throw new UnsupportedOperationException(); }

      public void handle(AsyncResult<T> asyncResult) {
        if (asyncResult.succeeded()) {
          complete(asyncResult.result());
        } else {
          fail(asyncResult.cause());
        }
      }
    }

    DefaultCompleterTestFuture<Object> successFuture = new DefaultCompleterTestFuture<>();
    successFuture.handle(succeededAsyncResult);
    assertTrue(successFuture.succeeded);
    assertEquals(succeededAsyncResult.result(), successFuture.result);

    DefaultCompleterTestFuture<Object> failureFuture = new DefaultCompleterTestFuture<>();
    failureFuture.handle(failedAsyncResult);
    assertTrue(failureFuture.failed);
    assertEquals(failedAsyncResult.cause(), failureFuture.cause);
  }

  class Checker<T> {

    private final Future<T> future;
    private final AtomicReference<AsyncResult<T>> result = new AtomicReference<>();
    private final AtomicInteger count = new AtomicInteger();

    Checker(Future<T> future) {
      future.onComplete(ar -> {
        count.incrementAndGet();
        result.set(ar);
      });
      this.future = future;
    }

    void assertNotCompleted() {
      assertFalse(future.isComplete());
      assertFalse(future.succeeded());
      assertFalse(future.failed());
      assertNull(future.cause());
      assertNull(future.result());
      assertEquals(0, count.get());
      assertNull(result.get());
    }

    void assertSucceeded(T expected) {
      assertTrue(future.isComplete());
      assertTrue(future.succeeded());
      assertFalse(future.failed());
      assertNull(future.cause());
      assertEquals(expected, future.result());
      assertEquals(1, count.get());
      AsyncResult<T> ar = result.get();
      assertNotNull(ar);
      assertTrue(ar.succeeded());
      assertFalse(ar.failed());
      assertNull(ar.cause());
      assertEquals(expected, future.result());
    }

    void assertFailed(Throwable expected) {
      assertEquals(expected, assertFailed());
    }

    Throwable assertFailed() {
      assertTrue(future.isComplete());
      assertFalse(future.succeeded());
      assertTrue(future.failed());
      assertEquals(null, future.result());
      assertEquals(1, count.get());
      AsyncResult<T> ar = result.get();
      assertNotNull(ar);
      assertFalse(ar.succeeded());
      assertTrue(ar.failed());
      assertNull(ar.result());
      return future.cause();
    }
  }

  @Test
  public void testUncompletedAsyncResultMap() {
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    AsyncResult<String> res = asyncResult(f);
    AsyncResult<Integer> map1 = res.map(String::length);
    AsyncResult<Integer> map2 = res.map(17);
    assertNull(map1.result());
    assertNull(map1.cause());
    assertNull(map2.result());
    assertNull(map2.cause());
  }

  @Test
  public void testSucceededAsyncResultMap() {
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    AsyncResult<String> res = asyncResult(f);
    AsyncResult<Integer> map1 = res.map(String::length);
    AsyncResult<Integer> map2 = res.map(17);
    p.complete("foobar");
    assertEquals(6, (int)map1.result());
    assertNull(map1.cause());
    assertEquals(17, (int)map2.result());
    assertNull(map2.cause());
  }

  @Test
  public void testFailedAsyncResultMap() {
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    AsyncResult<String> res = asyncResult(f);
    AsyncResult<Integer> map1 = res.map(String::length);
    AsyncResult<Integer> map2 = res.map(17);
    Throwable cause = new Throwable();
    p.fail(cause);
    assertNull(map1.result());
    assertSame(cause, map1.cause());
    assertNull(map2.result());
    assertSame(cause, map2.cause());
  }

  @Test
  public void testAsyncResultMapEmpty() {
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    AsyncResult<String> res = asyncResult(f);
    AsyncResult<Integer> map = res.mapEmpty();
    p.complete("foobar");
    assertNull(null, map.result());
    assertNull(map.cause());
  }

  @Test
  public void testSucceededFutureRecover() {
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    Future<String> r = f.recover(t -> Future.succeededFuture(t.getMessage()));
    p.complete("yeah");
    assertTrue(r.succeeded());
    assertEquals(r.result(), "yeah");
  }

  @Test
  public void testFailedFutureRecover() {
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    Future<String> r = f.recover(t -> Future.succeededFuture(t.getMessage()));
    p.fail("recovered");
    assertTrue(r.succeeded());
    assertEquals(r.result(), "recovered");
  }

  @Test
  public void testFailedMapperFutureRecover() {
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    Future<String> r = f.recover(t -> {
      throw new RuntimeException("throw");
    });
    p.fail("recovered");
    assertTrue(r.failed());
    assertEquals(r.cause().getMessage(), "throw");
  }

  @Test
  public void testUncompletedAsyncResultOtherwise() {
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    AsyncResult<String> res = asyncResult(f);
    testUncompletedAsyncResultOtherwise(res);
  }

  @Test
  public void testUncompletedFutureOtherwise() {
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    testUncompletedAsyncResultOtherwise(f);
  }

  private void testUncompletedAsyncResultOtherwise(AsyncResult<String> res) {
    AsyncResult<String> ar1 = res.otherwise("something-else");
    assertFalse(ar1.succeeded());
    assertFalse(ar1.failed());
    assertNull(ar1.result());
    assertNull(ar1.cause());
  }

  @Test
  public void testUncompletedAsyncResultOtherwiseApplyFunction() {
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    AsyncResult<String> res = asyncResult(f);
    testUncompletedOtherwiseApplyFunction(res);
  }

  @Test
  public void testUncompletedFutureOtherwiseApplyFunction() {
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    testUncompletedOtherwiseApplyFunction(f);
  }

  private void testUncompletedOtherwiseApplyFunction(AsyncResult<String> res) {
    AsyncResult<String> ar1 = res.otherwise(Throwable::getMessage);
    assertFalse(ar1.succeeded());
    assertFalse(ar1.failed());
    assertNull(ar1.result());
    assertNull(ar1.cause());
  }

  @Test
  public void testSucceededAsyncResultOtherwise() {
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    AsyncResult<String> res = asyncResult(f);
    testSucceededOtherwise(res, p);
  }

  @Test
  public void testSucceededFutureOtherwise() {
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    testSucceededOtherwise(f, p);
  }

  private void testSucceededOtherwise(AsyncResult<String> res, Promise<String> p) {
    AsyncResult<String> ar = res.otherwise(Throwable::getMessage);
    p.complete("foobar");
    assertTrue(ar.succeeded());
    assertFalse(ar.failed());
    assertEquals("foobar", ar.result());
    assertNull(ar.cause());
  }

  @Test
  public void testSucceededAsyncResultOtherwiseApplyFunction() {
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    AsyncResult<String> res = asyncResult(f);
    testSucceededOtherwiseApplyFunction(res, p);
  }

  @Test
  public void testSucceededFutureOtherwiseApplyFunction() {
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    testSucceededOtherwiseApplyFunction(f, p);
  }

  private void testSucceededOtherwiseApplyFunction(AsyncResult<String> res, Promise<String> p) {
    AsyncResult<String> ar = res.otherwise("whatever");
    p.complete("foobar");
    assertTrue(ar.succeeded());
    assertFalse(ar.failed());
    assertEquals("foobar", ar.result());
    assertNull(ar.cause());
  }

  @Test
  public void testFailedAsyncResultOtherwise() {
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    AsyncResult<String> res = asyncResult(f);
    testFailedOtherwise(res, p);
  }

  @Test
  public void testFailedFutureOtherwise() {
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    testFailedOtherwise(f, p);
  }

  private void testFailedOtherwise(AsyncResult<String> res, Promise<String> p) {
    AsyncResult<String> map1 = res.otherwise("something-else");
    Throwable cause = new Throwable("the-failure");
    p.fail(cause);
    assertTrue(map1.succeeded());
    assertFalse(map1.failed());
    assertEquals("something-else", map1.result());
    assertNull(map1.cause());
  }

  @Test
  public void testFailedAsyncResultOtherwiseApplyFunction() {
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    AsyncResult<String> res = asyncResult(f);
    testFailedOtherwiseApplyFunction(res, p);
  }

  @Test
  public void testFailedFutureOtherwiseApplyFunction() {
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    testFailedOtherwiseApplyFunction(f, p);
  }

  private void testFailedOtherwiseApplyFunction(AsyncResult<String> res, Promise<String> p) {
    AsyncResult<String> map1 = res.otherwise(Throwable::getMessage);
    Throwable cause = new Throwable("the-failure");
    p.fail(cause);
    assertTrue(map1.succeeded());
    assertFalse(map1.failed());
    assertEquals("the-failure", map1.result());
    assertNull(map1.cause());
  }

  @Test
  public void testOtherwiseWithNullFunction() {
    Promise<Integer> p = Promise.promise();
    Future<Integer> fut = p.future();
    try {
      fut.otherwise((Function<Throwable, Integer>) null);
      fail();
    } catch (NullPointerException ignore) {
    }
    try {
      asyncResult(fut).otherwise((Function<Throwable, Integer>) null);
      fail();
    } catch (NullPointerException ignore) {
    }
  }

  @Test
  public void testAsyncResultOtherwiseEmpty() {
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    AsyncResult<String> res = asyncResult(f);
    testOtherwiseEmpty(res, p);
  }

  @Test
  public void testFutureOtherwiseEmpty() {
    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    testOtherwiseEmpty(f, p);
  }

  @Test
  public void testToString() {
    assertEquals("Future{unresolved}", Promise.promise().future().toString());
    assertEquals("Future{result=abc}", Future.succeededFuture("abc").toString());
    assertEquals("Future{cause=It's like that, and that's the way it is}", Future.failedFuture("It's like that, and that's the way it is").toString());

    Promise<String> p = Promise.promise();
    Future<String> f = p.future();
    p.complete("abc");
    assertEquals("Future{result=abc}", f.toString());

    p = Promise.promise();
    f = p.future();
    p.fail("abc");
    assertEquals("Future{cause=abc}", f.toString());
  }

  @Test
  public void testReleaseListenerAfterCompletion() throws Exception {
    Promise<String> promise = Promise.promise();
    Future<String> f = promise.future();
    Field handlerField = f.getClass().getSuperclass().getDeclaredField("listener");
    handlerField.setAccessible(true);
    f.onComplete(ar -> {});
    promise.complete();
    assertNull(handlerField.get(f));
    f.onComplete(ar -> {});
    assertNull(handlerField.get(f));
    promise = Promise.promise();
    f = promise.future();
    f.onComplete(ar -> {});
    promise.fail("abc");
    assertNull(handlerField.get(f));
    f.onComplete(ar -> {});
    assertNull(handlerField.get(f));
  }

  @Test
  public void testSetNullHandler() throws Exception {
    Promise<String> promise = Promise.promise();
    try {
      promise.future().onComplete(null);
      fail();
    } catch (NullPointerException ignore) {
    }
    promise.complete();
    try {
      promise.future().onComplete(null);
      fail();
    } catch (NullPointerException ignore) {
    }
  }

  @Test
  public void testSucceedOnContext() throws Exception {
    waitFor(4);
    Object result = new Object();
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    CompletableFuture<Thread> latch = new CompletableFuture<>();
    ctx.runOnContext(v -> {
      latch.complete(Thread.currentThread());
    });
    Thread elThread = latch.get(10, TimeUnit.SECONDS);

    //
    CountDownLatch latch1 = new CountDownLatch(1);
    Promise<Object> promise1 = ctx.promise();
    vertx.runOnContext(v -> {
      promise1.complete(result);
      latch1.countDown();
    });
    awaitLatch(latch1);
    promise1.future().onComplete(ar -> {
      assertSame(elThread, Thread.currentThread());
      assertTrue(ar.succeeded());
      assertSame(result, ar.result());
      complete();
    });

    //
    Promise<Object> promise2 = ctx.promise();
    promise2.future().onComplete(ar -> {
      assertSame(elThread, Thread.currentThread());
      assertTrue(ar.succeeded());
      assertSame(result, ar.result());
      complete();
    });
    vertx.runOnContext(v -> promise2.complete(result));

    //
    Promise<Object> promise3 = ctx.promise();
    promise3.complete(result);
    promise3.future().onComplete(ar -> {
      assertSame(elThread, Thread.currentThread());
      assertTrue(ar.succeeded());
      assertSame(result, ar.result());
      complete();
    });

    //
    Promise<Object> promise4 = ctx.promise();
    promise4.future().onComplete(ar -> {
      assertSame(elThread, Thread.currentThread());
      assertTrue(ar.succeeded());
      assertSame(result, ar.result());
      complete();
    });
    promise4.complete(result);

    await();
  }


  private void testOtherwiseEmpty(AsyncResult<String> res, Promise<String> p) {
    AsyncResult<String> otherwise = res.otherwiseEmpty();
    Throwable cause = new Throwable("the-failure");
    p.fail(cause);
    assertTrue(otherwise.succeeded());
    assertFalse(otherwise.failed());
    assertEquals(null, otherwise.result());
    assertNull(otherwise.cause());
  }

  private <T> AsyncResult<T> asyncResult(Future<T> fut) {
    return new AsyncResult<T>() {
      @Override
      public T result() {
        return fut.result();
      }

      @Override
      public Throwable cause() {
        return fut.cause();
      }

      @Override
      public boolean succeeded() {
        return fut.succeeded();
      }

      @Override
      public boolean failed() {
        return fut.failed();
      }
    };
  }

  @Test
  public void testSeveralHandlers1() {
    waitFor(2);
    Promise<String> promise = Promise.promise();
    Future<String> fut = promise.future();
    fut.onComplete(ar -> {
      complete();
    });
    fut.onComplete(ar -> {
      complete();
    });
    promise.complete();
    await();
  }

  @Test
  public void testSeveralHandlers2() {
    waitFor(2);
    Promise<String> promise = Promise.promise();
    promise.complete();
    Future<String> fut = promise.future();
    fut.onComplete(ar -> {
      complete();
    });
    fut.onComplete(ar -> {
      complete();
    });
    await();
  }

  @Test
  public void testSeveralHandlers3() {
    waitFor(2);
    Promise<String> promise = Promise.promise();
    Future<String> fut = promise.future();
    fut.onComplete(ar -> {
      complete();
    });
    promise.complete();
    fut.onComplete(ar -> {
      complete();
    });
    await();
  }

  @Test
  public void testSuccessNotification() {
    waitFor(2);
    Promise<String> promise = Promise.promise();
    Future<String> fut = promise.future();
    fut.onComplete(onSuccess(res -> {
      assertEquals("foo", res);
      complete();
    }));
    fut.onSuccess(res -> {
      assertEquals("foo", res);
      complete();
    });
    fut.onFailure(err -> {
      fail();
    });
    promise.complete("foo");
    await();
  }

  @Test
  public void testFailureNotification() {
    waitFor(2);
    Promise<String> promise = Promise.promise();
    Future<String> fut = promise.future();
    Throwable failure = new Throwable();
    fut.onComplete(onFailure(err -> {
      assertEquals(failure, err);
      complete();
    }));
    fut.onSuccess(res -> {
      fail();
    });
    fut.onFailure(err -> {
      assertEquals(failure, err);
      complete();
    });
    promise.fail(failure);
    await();
  }

  @Test
  public void testVoidFuture() {
    waitFor(2);
    Promise<Void> promise = Promise.promise();
    promise.complete();
    List<Future<Void>> promises = Arrays.asList(promise.future(), Future.succeededFuture());
    promises.forEach(fut -> {
      fut
        .map(v -> "null")
        .onComplete(onSuccess(s -> {
        assertEquals("null", s);
        complete();
      }));
    });
    await();
  }

  @Test
  public void testPromiseUsedAsHandler() {
    Promise<Void> promise1 = Promise.promise();
    Promise<Void> promise2 = Promise.promise();
    promise1.future().onComplete(promise2);
    promise2.future().onComplete(onSuccess(v -> {
      testComplete();
    }));
    promise1.complete();
    await();
  }

  @Test
  public void testToCompletionStageTrampolining() {
    waitFor(2);
    Thread mainThread = Thread.currentThread();
    Future<String> success = Future.succeededFuture("Yo");
    success.toCompletionStage()
      .thenAccept(str -> {
        assertEquals("Yo", str);
        assertSame(mainThread, Thread.currentThread());
        complete();
      });
    Future<String> failed = Future.failedFuture(new RuntimeException("Woops"));
    failed.toCompletionStage()
      .whenComplete((str, err) -> {
        assertNull(str);
        assertTrue(err instanceof RuntimeException);
        assertEquals("Woops", err.getMessage());
        assertSame(mainThread, Thread.currentThread());
        complete();
      });
    await();
  }

  @Test
  public void testToCompletionStageDelayedCompletion() {
    waitFor(2);
    Thread mainThread = Thread.currentThread();
    Promise<String> willSucceed = Promise.promise();
    Promise<String> willFail = Promise.promise();

    willSucceed.future().toCompletionStage().whenComplete((str, err) -> {
      assertEquals("Yo", str);
      assertNull(err);
      assertNotSame(mainThread, Thread.currentThread());
      complete();
    });

    willFail.future().toCompletionStage().whenComplete((str, err) -> {
      assertNull(str);
      assertTrue(err instanceof RuntimeException);
      assertEquals("Woops", err.getMessage());
      assertNotSame(mainThread, Thread.currentThread());
      complete();
    });

    disableThreadChecks();
    new Thread(() -> willSucceed.complete("Yo")).start();
    new Thread(() -> willFail.fail(new RuntimeException("Woops"))).start();
    await();
  }

  @Test
  public void testFromCompletionStageTrampolining() {
    waitFor(2);
    disableThreadChecks();

    AtomicReference<Thread> successSupplierThread = new AtomicReference<>();
    CompletableFuture<String> willSucceed = new CompletableFuture<>();

    AtomicReference<Thread> failureSupplierThread = new AtomicReference<>();
    CompletableFuture<String> willFail = new CompletableFuture<>();

    Future.fromCompletionStage(willSucceed).onSuccess(str -> {
      assertEquals("Ok", str);
      assertSame(successSupplierThread.get(), Thread.currentThread());
      complete();
    });

    Future.fromCompletionStage(willFail).onFailure(err -> {
      assertTrue(err instanceof RuntimeException);
      assertEquals("Woops", err.getMessage());
      assertSame(failureSupplierThread.get(), Thread.currentThread());
      complete();
    });

    ForkJoinPool fjp = ForkJoinPool.commonPool();
    fjp.execute(() -> {
      successSupplierThread.set(Thread.currentThread());
      willSucceed.complete("Ok");
    });
    fjp.execute(() -> {
      failureSupplierThread.set(Thread.currentThread());
      willFail.completeExceptionally(new RuntimeException("Woops"));
    });

    await();
  }

  @Test
  public void testFromCompletionStageWithContext() {
    waitFor(2);
    Context context = vertx.getOrCreateContext();

    AtomicReference<Thread> successSupplierThread = new AtomicReference<>();
    CompletableFuture<String> willSucceed = new CompletableFuture<>();

    AtomicReference<Thread> failureSupplierThread = new AtomicReference<>();
    CompletableFuture<String> willFail = new CompletableFuture<>();

    Future.fromCompletionStage(willSucceed, context).onSuccess(str -> {
      assertEquals("Ok", str);
      assertNotSame(successSupplierThread.get(), Thread.currentThread());
      assertEquals(context, vertx.getOrCreateContext());
      assertTrue(Thread.currentThread().getName().startsWith("vert.x-eventloop-thread"));
      complete();
    });

    Future.fromCompletionStage(willFail, context).onFailure(err -> {
      assertTrue(err instanceof RuntimeException);
      assertEquals("Woops", err.getMessage());
      assertNotSame(failureSupplierThread.get(), Thread.currentThread());
      assertEquals(context, vertx.getOrCreateContext());
      assertTrue(Thread.currentThread().getName().startsWith("vert.x-eventloop-thread"));
      complete();
    });

    ForkJoinPool fjp = ForkJoinPool.commonPool();
    fjp.execute(() -> {
      successSupplierThread.set(Thread.currentThread());
      willSucceed.complete("Ok");
    });
    fjp.execute(() -> {
      failureSupplierThread.set(Thread.currentThread());
      willFail.completeExceptionally(new RuntimeException("Woops"));
    });

    await();
  }

  @Test
  public void testCompletedFuturesContext() throws Exception {
    waitFor(4);

    Thread testThread = Thread.currentThread();
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();


    CompletableFuture<Thread> cf = new CompletableFuture<>();
    context.runOnContext(v -> cf.complete(Thread.currentThread()));
    Thread contextThread = cf.get();

    Future.succeededFuture().onSuccess(v -> {
      assertSame(testThread, Thread.currentThread());
      assertNull(Vertx.currentContext());
      complete();
    });

    context.succeededFuture().onSuccess(v -> {
      assertNotSame(testThread, Thread.currentThread());
      assertSame(context, Vertx.currentContext());
      assertSame(contextThread, Thread.currentThread());
      complete();
    });

    Future.failedFuture(new Exception()).onFailure(v -> {
      assertSame(testThread, Thread.currentThread());
      assertNull(Vertx.currentContext());
      complete();
    });

    context.failedFuture(new Exception()).onFailure(v -> {
      assertNotSame(testThread, Thread.currentThread());
      assertSame(context, Vertx.currentContext());
      assertSame(contextThread, Thread.currentThread());
      complete();
    });

    await();
  }
}
