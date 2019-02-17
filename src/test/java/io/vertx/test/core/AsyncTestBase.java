/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.core;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.internal.ArrayComparisonFailure;
import org.junit.rules.TestName;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AsyncTestBase {

  private static final Logger log = LoggerFactory.getLogger(AsyncTestBase.class);

  private CountDownLatch latch;
  private volatile Throwable throwable;
  private volatile Thread thrownThread;
  private volatile boolean testCompleteCalled;
  private volatile boolean awaitCalled;
  private boolean threadChecksEnabled = true;
  private volatile boolean tearingDown;
  private volatile String mainThreadName;
  private volatile boolean lateFailure;
  private Map<String, Exception> threadNames = new ConcurrentHashMap<>();
  @Rule
  public TestName name = new TestName();


  protected void setUp() throws Exception {
    log.info("Starting test: " + this.getClass().getSimpleName() + "#" + name.getMethodName());
    mainThreadName = Thread.currentThread().getName();
    tearingDown = false;
    waitFor(1);
    throwable = null;
    testCompleteCalled = false;
    awaitCalled = false;
    threadNames.clear();
    lateFailure = false;
  }

  protected void tearDown() throws Exception {
    tearingDown = true;
    afterAsyncTestBase();
  }

  @Before
  public void before() throws Exception {
    setUp();
  }

  @After
  public void after() throws Exception {
    tearDown();
  }

  protected synchronized void waitFor(int count) {
    latch = new CountDownLatch(count);
  }

  protected synchronized void waitForMore(int count) {
    latch = new CountDownLatch(count + (int)latch.getCount());
  }

  protected synchronized void complete() {
    if (tearingDown) {
      throw new IllegalStateException("testComplete called after test has completed");
    }
    checkThread();
    if (testCompleteCalled) {
      throw new IllegalStateException("already complete");
    }
    latch.countDown();
    if (latch.getCount() == 0) {
      testCompleteCalled = true;
    }
  }

  protected void testComplete() {
    if (tearingDown) {
      throw new IllegalStateException("testComplete called after test has completed");
    }
    checkThread();
    if (testCompleteCalled) {
      throw new IllegalStateException("testComplete() already called");
    }
    testCompleteCalled = true;
    latch.countDown();
  }

  protected void await() {
    await(2, TimeUnit.MINUTES);
  }

  public void await(long delay, TimeUnit timeUnit) {
    if (awaitCalled) {
      throw new IllegalStateException("await() already called");
    }
    awaitCalled = true;
    try {
      boolean ok = latch.await(delay, timeUnit);
      if (!ok) {
        // timed out
        throw new IllegalStateException("Timed out in waiting for test complete");
      } else {
        rethrowError();
      }
    } catch (InterruptedException e) {
      throw new IllegalStateException("Test thread was interrupted!");
    }
  }

  private void rethrowError() {
    if (throwable != null) {
      if (throwable instanceof Error) {
        throw (Error)throwable;
      } else if (throwable instanceof RuntimeException) {
        throw (RuntimeException)throwable;
      } else {
        // Unexpected throwable- Should never happen
        throw new IllegalStateException(throwable);
      }

    }
  }

  protected void disableThreadChecks() {
    threadChecksEnabled = false;
  }

  protected void afterAsyncTestBase() {
    if (throwable != null && thrownThread != Thread.currentThread() && !awaitCalled) {
      // Throwable caught from non main thread
      throw new IllegalStateException("Assert or failure from non main thread but no await() on main thread", throwable);
    }
    if (lateFailure) {
      throw new IllegalStateException("Test reported a failure after completion");
    }
    for (Map.Entry<String, Exception> entry: threadNames.entrySet()) {
      if (!entry.getKey().equals(mainThreadName)) {
        if (threadChecksEnabled && !entry.getKey().startsWith("vert.x-")) {
          IllegalStateException is = new IllegalStateException("Non Vert.x thread! :" + entry.getKey());
          is.setStackTrace(entry.getValue().getStackTrace());
          throw is;
        }
      }
    }

  }

  private void handleThrowable(Throwable t) {
    if (testCompleteCalled) {
      lateFailure = true;
      throw new IllegalStateException("assert or failure occurred after test has completed");
    }
    throwable = t;
    t.printStackTrace();
    thrownThread = Thread.currentThread();
    latch.countDown();
    if (t instanceof AssertionError) {
      throw (AssertionError)t;
    }
  }

  protected void clearThrown() {
    throwable = null;
  }

  protected void checkThread() {
    threadNames.put(Thread.currentThread().getName(), new Exception());
  }

  protected void assertTrue(String message, boolean condition) {
    checkThread();
    try {
      Assert.assertTrue(message, condition);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertFalse(boolean condition) {
    checkThread();
    try {
      Assert.assertFalse(condition);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertArrayEquals(String message, char[] expecteds, char[] actuals) throws ArrayComparisonFailure {
    checkThread();
    try {
      Assert.assertArrayEquals(message, expecteds, actuals);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertSame(String message, Object expected, Object actual) {
    checkThread();
    try {
      Assert.assertSame(message, expected, actual);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertEquals(long expected, long actual) {
    checkThread();
    try {
      Assert.assertEquals(expected, actual);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertNull(Object object) {
    checkThread();
    try {
      Assert.assertNull(object);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertFalse(String message, boolean condition) {
    checkThread();
    try {
      Assert.assertFalse(message, condition);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void fail(String message) {
    checkThread();
    try {
      Assert.fail(message);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertNull(String message, Object object) {
    checkThread();
    try {
      Assert.assertNull(message, object);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertArrayEquals(String message, float[] expecteds, float[] actuals, float delta) throws ArrayComparisonFailure {
    checkThread();
    try {
      Assert.assertArrayEquals(message, expecteds, actuals, delta);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  @Deprecated
  protected void assertEquals(String message, double expected, double actual) {
    checkThread();
    try {
      Assert.assertEquals(message, expected, actual);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }


  protected void assertArrayEquals(String message, double[] expecteds, double[] actuals, double delta) throws ArrayComparisonFailure {
    checkThread();
    try {
      Assert.assertArrayEquals(message, expecteds, actuals, delta);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertArrayEquals(String message, Object[] expecteds, Object[] actuals) throws ArrayComparisonFailure {
    checkThread();
    try {
      Assert.assertArrayEquals(message, expecteds, actuals);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertArrayEquals(String message, short[] expecteds, short[] actuals) throws ArrayComparisonFailure {
    checkThread();
    try {
      Assert.assertArrayEquals(message, expecteds, actuals);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertArrayEquals(short[] expecteds, short[] actuals) {
    checkThread();
    try {
      Assert.assertArrayEquals(expecteds, actuals);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertArrayEquals(long[] expecteds, long[] actuals) {
    checkThread();
    try {
      Assert.assertArrayEquals(expecteds, actuals);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertNotNull(Object object) {
    checkThread();
    try {
      Assert.assertNotNull(object);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertEquals(Object expected, Object actual) {
    checkThread();
    try {
      Assert.assertEquals(expected, actual);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertEquals(String message, Object expected, Object actual) {
    checkThread();
    try {
      Assert.assertEquals(message, expected, actual);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertTrue(boolean condition) {
    checkThread();
    try {
      Assert.assertTrue(condition);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertArrayEquals(Object[] expecteds, Object[] actuals) {
    checkThread();
    try {
      Assert.assertArrayEquals(expecteds, actuals);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertNotNull(String message, Object object) {
    checkThread();
    try {
      Assert.assertNotNull(message, object);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertEquals(String message, double expected, double actual, double delta) {
    checkThread();
    try {
      Assert.assertEquals(message, expected, actual, delta);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void fail() {
    checkThread();
    try {
      Assert.fail();
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void fail(Throwable cause) {
    checkThread();
    handleThrowable(cause);
  }

  protected void assertSame(Object expected, Object actual) {
    checkThread();
    try {
      Assert.assertSame(expected, actual);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertEquals(String message, long expected, long actual) {
    checkThread();
    try {
      Assert.assertEquals(message, expected, actual);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertArrayEquals(String message, byte[] expecteds, byte[] actuals) throws ArrayComparisonFailure {
    checkThread();
    try {
      Assert.assertArrayEquals(message, expecteds, actuals);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertArrayEquals(String message, long[] expecteds, long[] actuals) throws ArrayComparisonFailure {
    checkThread();
    try {
      Assert.assertArrayEquals(message, expecteds, actuals);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertEquals(double expected, double actual, double delta) {
    checkThread();
    try {
      Assert.assertEquals(expected, actual, delta);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected <T> void assertThat(T actual, Matcher<T> matcher) {
    checkThread();
    try {
      Assert.assertThat(actual, matcher);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  @Deprecated
  protected void assertEquals(String message, Object[] expecteds, Object[] actuals) {
    checkThread();
    try {
      Assert.assertEquals(message, expecteds, actuals);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  @Deprecated
  protected void assertEquals(Object[] expecteds, Object[] actuals) {
    checkThread();
    try {
      Assert.assertEquals(expecteds, actuals);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertNotSame(String message, Object unexpected, Object actual) {
    checkThread();
    try {
      Assert.assertNotSame(message, unexpected, actual);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected <T> void assertThat(String reason, T actual, Matcher<T> matcher) {
    checkThread();
    try {
      Assert.assertThat(reason, actual, matcher);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertArrayEquals(float[] expecteds, float[] actuals, float delta) {
    checkThread();
    try {
      Assert.assertArrayEquals(expecteds, actuals, delta);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertNotSame(Object unexpected, Object actual) {
    checkThread();
    try {
      Assert.assertNotSame(unexpected, actual);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertArrayEquals(byte[] expecteds, byte[] actuals) {
    checkThread();
    try {
      Assert.assertArrayEquals(expecteds, actuals);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertArrayEquals(char[] expecteds, char[] actuals) {
    checkThread();
    try {
      Assert.assertArrayEquals(expecteds, actuals);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertArrayEquals(double[] expecteds, double[] actuals, double delta) {
    checkThread();
    try {
      Assert.assertArrayEquals(expecteds, actuals, delta);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertArrayEquals(int[] expecteds, int[] actuals) {
    checkThread();
    try {
      Assert.assertArrayEquals(expecteds, actuals);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  @Deprecated
  protected void assertEquals(double expected, double actual) {
    checkThread();
    try {
      Assert.assertEquals(expected, actual);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected void assertArrayEquals(String message, int[] expecteds, int[] actuals) throws ArrayComparisonFailure {
    checkThread();
    try {
      Assert.assertArrayEquals(message, expecteds, actuals);
    } catch (AssertionError e) {
      handleThrowable(e);
    }
  }

  protected <T> Handler<AsyncResult<T>> onFailure(Consumer<Throwable> consumer) {
    return result -> {
      assertFalse(result.succeeded());
      consumer.accept(result.cause());
    };
  }

  protected void awaitLatch(CountDownLatch latch) throws InterruptedException {
    awaitLatch(latch, 10, TimeUnit.SECONDS);
  }

  protected void awaitLatch(CountDownLatch latch, long timeout, TimeUnit unit) throws InterruptedException {
    assertTrue(latch.await(timeout, unit));
  }

  public static void assertWaitUntil(BooleanSupplier supplier) {
    assertWaitUntil(supplier, 10000);
  }

  public static void waitUntil(BooleanSupplier supplier) {
    waitUntil(supplier, 10000);
  }

  public static <T> void waitUntilEquals(T value, Supplier<T> supplier) {
    waitUntil(() -> Objects.equals(value, supplier.get()), 10000);
  }

  public static void assertWaitUntil(BooleanSupplier supplier, long timeout) {
    if (!waitUntil(supplier, timeout)) {
      throw new IllegalStateException("Timed out");
    }
  }

  public static void assertWaitUntil(BooleanSupplier supplier, long timeout, String reason) {
    if (!waitUntil(supplier, timeout)) {
      throw new IllegalStateException("Timed out: " + reason);
    }
  }

  public static boolean waitUntil(BooleanSupplier supplier, long timeout) {
    long start = System.currentTimeMillis();
    while (true) {
      if (supplier.getAsBoolean()) {
        return true;
      }
      try {
        Thread.sleep(10);
      } catch (InterruptedException ignore) {
      }
      long now = System.currentTimeMillis();
      if (now - start > timeout) {
        return false;
      }
    }
  }

  protected <T> Handler<AsyncResult<T>> onSuccess(Consumer<T> consumer) {
    return result -> {
      if (result.failed()) {
        result.cause().printStackTrace();
        fail(result.cause().getMessage());
      } else {
        consumer.accept(result.result());
      }
    };
  }

  protected void close(Vertx vertx) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    vertx.close(ar -> {
      latch.countDown();
    });
    awaitLatch(latch);
  }

  protected void assertSameEventLoop(Context expected, Context actual) {
    assertSame(((ContextInternal)expected).nettyEventLoop(), ((ContextInternal)actual).nettyEventLoop());
  }
}
