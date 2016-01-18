/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.test.core;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FutureTest extends VertxTestBase {

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
  public void testSetResultOnCompletedFuture() {
    ArrayList<Future<Object>> futures = new ArrayList<>();
    futures.add(Future.succeededFuture());
    futures.add(Future.succeededFuture());
    futures.add(Future.succeededFuture(new Object()));
    futures.add(Future.succeededFuture(new Object()));
    futures.add(Future.failedFuture(new Exception()));
    futures.add(Future.failedFuture(new Exception()));
    for (Future<Object> future : futures) {
      try {
        future.complete(new Object());
        fail();
      } catch (IllegalStateException ignore) {
      }
      try {
        future.complete(null);
        fail();
      } catch (IllegalStateException ignore) {
      }
      try {
        future.fail(new Exception());
        fail();
      } catch (IllegalStateException ignore) {
      }
    }
  }

  @Test
  public void testCallSetHandlerBeforeCompletion() {
    AtomicBoolean called = new AtomicBoolean();
    Future<Object> future = Future.future();
    future.setHandler(result -> {
      assertTrue(result.succeeded());
      assertFalse(result.failed());
      assertEquals(null, result.result());
      assertEquals(null, result.cause());
      called.set(true);
    });
    assertFalse(called.get());
    future.complete(null);
    assertTrue(called.get());
    called.set(false);
    Object foo = new Object();
    future = Future.future();
    future.setHandler(result -> {
      called.set(true);
      assertTrue(result.succeeded());
      assertFalse(result.failed());
      assertEquals(foo, result.result());
      assertEquals(null, result.cause());
    });
    assertFalse(called.get());
    future.complete(foo);
    assertTrue(called.get());
    called.set(false);
    Exception cause = new Exception();
    future = Future.future();
    future.setHandler(result -> {
      called.set(true);
      assertFalse(result.succeeded());
      assertTrue(result.failed());
      assertEquals(null, result.result());
      assertEquals(cause, result.cause());
    });
    assertFalse(called.get());
    future.fail(cause);
    assertTrue(called.get());
  }

  @Test
  public void testCallSetHandlerAfterCompletion() {
    AtomicBoolean called = new AtomicBoolean();
    Future<Object> future = Future.succeededFuture();
    future.setHandler(result -> {
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
    future.setHandler(result -> {
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
    future.setHandler(result -> {
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
    Consumer<Handler<AsyncResult<String>>> consumer = handler -> {
      handler.handle(Future.succeededFuture("the-result"));
    };
    Future<String> fut = Future.future();
    consumer.accept(fut.completer());
    assertTrue(fut.isComplete());
    assertTrue(fut.succeeded());
    assertEquals("the-result", fut.result());
  }

  @Test
  public void testFailFutureToHandler() {
    Throwable cause = new Throwable();
    Consumer<Handler<AsyncResult<String>>> consumer = handler -> {
      handler.handle(Future.failedFuture(cause));
    };
    Future<String> fut = Future.future();
    consumer.accept(fut.completer());
    assertTrue(fut.isComplete());
    assertTrue(fut.failed());
    assertEquals(cause, fut.cause());
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
    Future<String> f1 = Future.future();
    Future<Integer> f2 = Future.future();
    CompositeFuture composite = all.apply(f1, f2);
    assertNotCompleted(composite);
    assertEquals(null, composite.<String>result(0));
    assertEquals(null, composite.<Integer>result(1));
    f1.complete("something");
    assertNotCompleted(composite);
    assertEquals("something", composite.result(0));
    assertEquals(null, composite.<Integer>result(1));
    f2.complete(3);
    assertSucceeded(composite, composite);
    assertEquals("something", composite.result(0));
    assertEquals(3, (int)composite.result(1));
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
    Future<String> f1 = Future.future();
    Future<Integer> f2 = Future.future();
    CompositeFuture composite = all.apply(f1, f2);
    f1.complete("s");
    Exception cause = new Exception();
    f2.fail(cause);
    assertFailed(composite, cause);
    assertEquals("s", composite.result(0));
    assertEquals(null, composite.<Integer>result(1));
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
    Future<String> f1 = Future.future();
    Future<Integer> f2 = Future.future();
    CompositeFuture composite = any.apply(f1, f2);
    assertNotCompleted(composite);
    assertEquals(null, composite.<String>result(0));
    assertEquals(null, composite.<Integer>result(1));
    f1.complete("something");
    assertSucceeded(composite, composite);
    f2.complete(3);
    assertSucceeded(composite, composite);
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
    Future<String> f1 = Future.future();
    Future<Integer> f2 = Future.future();
    CompositeFuture composite = any.apply(f1, f2);
    f1.fail("failure");
    assertNotCompleted(composite);
    f2.complete(3);
    assertSucceeded(composite, composite);
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
    Future<String> f1 = Future.future();
    Future<Integer> f2 = Future.future();
    CompositeFuture composite = any.apply(f1, f2);
    f1.fail("failure");
    assertNotCompleted(composite);
    Throwable cause = new Exception();
    f2.fail(cause);
    assertFailed(composite, cause);
  }

  @Test
  public void testComposeSucceed() {
    Future<String> f1 = Future.future();
    Future<Integer> f2 = Future.future();
    f1.compose(string -> f2.complete(string.length()), f2);
    f1.complete("abcdef");
    assertSucceeded(f2, 6);
  }

  @Test
  public void testComposeFail() {
    Future<String> f1 = Future.future();
    Future<Integer> f2 = Future.future();
    f1.compose(string -> f2.complete(string.length()), f2);
    Exception cause = new Exception();
    f1.fail(cause);
    assertFailed(f2, cause);
  }

  @Test
  public void testComposeHandlerFail() {
    Future<String> f1 = Future.future();
    Future<Integer> f2 = Future.future();
    RuntimeException cause = new RuntimeException();
    f1.compose(string -> { throw cause; }, f2);
    f1.complete("foo");
    assertFailed(f2, cause);
  }

  @Test
  public void testComposeHandlerFailAfterCompletion() {
    Future<String> f1 = Future.future();
    Future<Integer> f2 = Future.future();
    RuntimeException cause = new RuntimeException();
    f1.compose(string -> {
      f2.complete(46);
      throw cause;
    }, f2);
    try {
      f1.complete("foo");
    } catch (Exception e) {
      assertEquals(cause, e);
    }
    assertSucceeded(f2, 46);
  }

  private <T> void assertSucceeded(Future<T> future, T expected) {
    assertTrue(future.isComplete());
    assertTrue(future.succeeded());
    assertFalse(future.failed());
    assertNull(future.cause());
    assertEquals(expected, future.result());
  }

  private <T> void assertFailed(Future<T> future, Throwable expected) {
    assertTrue(future.isComplete());
    assertFalse(future.succeeded());
    assertTrue(future.failed());
    assertEquals(expected, future.cause());
    assertEquals(null, future.result());
  }

  private <T> void assertNotCompleted(Future<T> future) {
    assertFalse(future.isComplete());
    assertFalse(future.succeeded());
    assertFalse(future.failed());
    assertNull(future.cause());
    assertNull(future.result());
  }
}
