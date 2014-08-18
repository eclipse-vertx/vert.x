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

import io.vertx.core.Future;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FutureTest extends VertxTestBase {

  @Test
  public void testStateAfterCompletion() {
    Object foo = new Object();
    Future<Object> future = Future.completedFuture(foo);
    assertTrue(future.succeeded());
    assertFalse(future.failed());
    assertTrue(future.complete());
    assertEquals(foo, future.result());
    assertNull(future.cause());
    Exception cause = new Exception();
    future = Future.completedFuture(cause);
    assertFalse(future.succeeded());
    assertTrue(future.failed());
    assertTrue(future.complete());
    assertNull(future.result());
    assertEquals(cause, future.cause());
  }

  @Test
  public void testSetResultOnCompletedFuture() {
    ArrayList<Future<Object>> futures = new ArrayList<>();
    futures.add(Future.completedFuture());
    futures.add(Future.future().setResult(null));
    futures.add(Future.completedFuture(new Object()));
    futures.add(Future.future().setResult(new Object()));
    futures.add(Future.completedFuture(new Exception()));
    futures.add(Future.future().setFailure(new Exception()));
    for (Future<Object> future : futures) {
      try {
        future.setResult(new Object());
        fail();
      } catch (IllegalStateException ignore) {
      }
      try {
        future.setResult(null);
        fail();
      } catch (IllegalStateException ignore) {
      }
      try {
        future.setFailure(new Exception());
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
    future.setResult(null);
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
    future.setResult(foo);
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
    future.setFailure(cause);
    assertTrue(called.get());
  }

  @Test
  public void testCallSetHandlerAfterCompletion() {
    AtomicBoolean called = new AtomicBoolean();
    Future<Object> future = Future.completedFuture();
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
    future = Future.completedFuture(foo);
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
    future = Future.completedFuture(cause);
    future.setHandler(result -> {
      assertFalse(result.succeeded());
      assertTrue(result.failed());
      assertEquals(null, result.result());
      assertEquals(cause, result.cause());
      called.set(true);
    });
    assertTrue(called.get());
  }
}
