/*
 * Copyright 2017 Red Hat, Inc.
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
import io.vertx.core.impl.NoStackTraceThrowable;
import org.junit.Test;

import java.util.function.Function;

import static io.vertx.core.AsyncResult.failedAsyncResult;
import static io.vertx.core.AsyncResult.succeededAsyncResult;

/**
 * @author <a href="mailto:ruslan.sennov@gmail.com">Ruslan Sennov</a>
 */
public class AsyncResultTest extends VertxTestBase {

  @Test
  public void testSucceededInit() {
    AsyncResult<Integer> ar = AsyncResult.succeededAsyncResult(42);
    assertTrue(ar.succeeded());
    assertFalse(ar.failed());
    assertTrue(ar.result() == 42);
    assertNull(ar.cause());
  }

  @Test
  public void testFailedInitWithMessage() {
    AsyncResult<Integer> ar = AsyncResult.failedAsyncResult("42");
    assertFalse(ar.succeeded());
    assertTrue(ar.failed());
    assertNull(ar.result());
    assertTrue(ar.cause() instanceof NoStackTraceThrowable);
    assertEquals(ar.cause().getMessage(), "42");
  }

  @Test
  public void testFailedInitWithThrowable() {
    AsyncResult<Integer> ar = AsyncResult.failedAsyncResult(new IllegalArgumentException("42"));
    assertFalse(ar.succeeded());
    assertTrue(ar.failed());
    assertNull(ar.result());
    assertTrue(ar.cause() instanceof IllegalArgumentException);
    assertEquals(ar.cause().getMessage(), "42");
  }

  @Test
  public void testMapWithNullFunction() {
    try {
      succeededAsyncResult(42).map((Function<Integer, String>) null);
      fail();
    } catch (NullPointerException ignore) {
    }
  }

  @Test
  public void testSucceededAsyncResultMap() {
    AsyncResult<String> res = succeededAsyncResult("foobar");
    AsyncResult<Integer> map1 = res.map(String::length);
    AsyncResult<Integer> map2 = res.map(17);
    assertEquals(6, (int)map1.result());
    assertNull(map1.cause());
    assertEquals(17, (int)map2.result());
    assertNull(map2.cause());
  }

  @Test
  public void testSucceededAsyncResultThrowMap() {
    AsyncResult<String> res = succeededAsyncResult("foobar");
    RuntimeException cause = new RuntimeException();
    AsyncResult<Integer> map = res.map(s -> {
      throw cause;
    });
    assertSame(cause, map.cause());
    assertNull(map.result());
  }

  @Test
  public void testFailedAsyncResultMap() {
    Throwable cause = new Throwable();
    AsyncResult<String> res = failedAsyncResult(cause);
    AsyncResult<Integer> map1 = res.map(String::length);
    AsyncResult<Integer> map2 = res.map(17);
    assertNull(map1.result());
    assertSame(cause, map1.cause());
    assertNull(map2.result());
    assertSame(cause, map2.cause());
  }

  @Test
  public void testFailedAsyncResultThrowMap() {
    Throwable cause = new Throwable();
    AsyncResult<String> res = failedAsyncResult(cause);
    AsyncResult<Integer> map = res.map(s -> {
      throw new RuntimeException();
    });
    assertSame(cause, map.cause());
    assertNull(map.result());
  }

  @Test
  public void testAsyncResultMapEmpty() {
    AsyncResult<String> res = succeededAsyncResult("foobar");
    AsyncResult<Integer> map = res.mapEmpty();
    assertNull(null, map.result());
    assertNull(map.cause());
  }

  @Test
  public void testSucceededAsyncResultOtherwise() {
    AsyncResult<String> res = succeededAsyncResult("foobar");
    AsyncResult<String> ar = res.otherwise(Throwable::getMessage);
    assertTrue(ar.succeeded());
    assertFalse(ar.failed());
    assertEquals("foobar", ar.result());
    assertNull(ar.cause());
  }

  @Test
  public void testSucceededAsyncResultOtherwiseApplyFunction() {
    AsyncResult<String> res = succeededAsyncResult("foobar");
    AsyncResult<String> ar = res.otherwise("whatever");
    assertTrue(ar.succeeded());
    assertFalse(ar.failed());
    assertEquals("foobar", ar.result());
    assertNull(ar.cause());
  }

  @Test
  public void testFailedAsyncResultOtherwise() {
    Throwable cause = new Throwable("the-failure");
    AsyncResult<String> res = failedAsyncResult(cause);
    AsyncResult<String> map1 = res.otherwise("something-else");
    assertTrue(map1.succeeded());
    assertFalse(map1.failed());
    assertEquals("something-else", map1.result());
    assertNull(map1.cause());
  }

  @Test
  public void testFailedAsyncResultOtherwiseApplyFunction() {
    Throwable cause = new Throwable("the-failure");
    AsyncResult<String> res = failedAsyncResult(cause);
    AsyncResult<String> map1 = res.otherwise(Throwable::getMessage);
    assertTrue(map1.succeeded());
    assertFalse(map1.failed());
    assertEquals("the-failure", map1.result());
    assertNull(map1.cause());
  }

  @Test
  public void testFailedAsyncResultOtherwiseApplyThrowFunction() {
    Throwable cause = new Throwable("the-failure");
    RuntimeException otherwise = new RuntimeException("the-failure2");
    AsyncResult<String> res = failedAsyncResult(cause);
    AsyncResult<String> map1 = res.otherwise(t -> {
      throw otherwise;
    });
    assertFalse(map1.succeeded());
    assertTrue(map1.failed());
    assertSame(otherwise, map1.cause());
    assertNull(map1.result());
  }

  @Test
  public void testOtherwiseWithNullFunction() {
    try {
      succeededAsyncResult(42).otherwise((Function<Throwable, Integer>) null);
      fail();
    } catch (NullPointerException ignore) {
    }
  }

  @Test
  public void testAsyncResultOtherwiseEmpty() {
    Throwable cause = new Throwable("the-failure");
    AsyncResult<String> res = failedAsyncResult(cause);
    AsyncResult<String> otherwise = res.otherwiseEmpty();
    assertTrue(otherwise.succeeded());
    assertFalse(otherwise.failed());
    assertEquals(null, otherwise.result());
    assertNull(otherwise.cause());
  }

}
