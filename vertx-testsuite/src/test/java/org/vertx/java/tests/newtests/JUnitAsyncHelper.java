package org.vertx.java.tests.newtests;

/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 */

import org.hamcrest.Matcher;
import org.junit.Assert;
import org.junit.internal.ArrayComparisonFailure;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class JUnitAsyncHelper {

  private final CountDownLatch latch = new CountDownLatch(1);
  private volatile Throwable throwable;
  private volatile boolean testCompleteCalled;
  private volatile boolean awaitCalled;

  public void testComplete() {
    if (testCompleteCalled) {
      throw new IllegalStateException("testComplete already called");
    }
    testCompleteCalled = true;
    latch.countDown();
  }

  public void await() {
    await(10, TimeUnit.SECONDS);
  }

  public void await(long delay, TimeUnit timeUnit) {
    if (awaitCalled) {
      throw new IllegalStateException("testComplete already called");
    }
    awaitCalled = true;
    try {
      boolean ok = latch.await(delay, timeUnit);
      if (!ok) {
        // timed out
        throw new IllegalStateException("Timed out in waiting for test complete");
      } else {
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
    } catch (InterruptedException e) {
      throw new IllegalStateException("Test thread was interrupted!");
    }
  }

  public void doAssert(Runnable runner) {
    try {
      runner.run();
    } catch (Throwable t) {
      throwable = t;
      latch.countDown();
    }
  }

  public boolean isAwaitCalled() {
    return awaitCalled;
  }

  public void close() {
    checkTestCompleteCalled();
  }

  // We override this too in case the test forgot to call close()
  @Override
  protected void finalize() {
    checkTestCompleteCalled();
  }

  private void checkTestCompleteCalled() {
    if (!testCompleteCalled && throwable == null) {
      testCompleteCalled = true;
      throw new IllegalStateException("Closed without calling testComplete()! Your test must call testComplete() before exiting");
    }
  }



//  private static void handleThrowable(Throwable t) {
//
//  }
//
//  public static void assertTrue(String message, boolean condition) {
//    try {
//      Assert.assertTrue(message, condition);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertFalse(boolean condition) {
//    try {
//      Assert.assertFalse(condition);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//
//  }
//
//  public static void assertArrayEquals(String message, char[] expecteds, char[] actuals) throws ArrayComparisonFailure {
//    try {
//      Assert.assertArrayEquals(message, expecteds, actuals);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertSame(String message, Object expected, Object actual) {
//    try {
//      Assert.assertSame(message, expected, actual);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertEquals(long expected, long actual) {
//    try {
//      Assert.assertEquals(expected, actual);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertNull(Object object) {
//    try {
//      Assert.assertNull(object);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertFalse(String message, boolean condition) {
//    try {
//      Assert.assertFalse(message, condition);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void fail(String message) {
//    try {
//      Assert.fail(message);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertNull(String message, Object object) {
//    try {
//      Assert.assertNull(message, object);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertArrayEquals(String message, float[] expecteds, float[] actuals, float delta) throws ArrayComparisonFailure {
//    try {
//      Assert.assertArrayEquals(message, expecteds, actuals, delta);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  @Deprecated
//  public static void assertEquals(String message, double expected, double actual) {
//    try {
//      Assert.assertEquals(message, expected, actual);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//
//  public static void assertArrayEquals(String message, double[] expecteds, double[] actuals, double delta) throws ArrayComparisonFailure {
//    try {
//      Assert.assertArrayEquals(message, expecteds, actuals, delta);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertArrayEquals(String message, Object[] expecteds, Object[] actuals) throws ArrayComparisonFailure {
//    try {
//      Assert.assertArrayEquals(message, expecteds, actuals);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertArrayEquals(String message, short[] expecteds, short[] actuals) throws ArrayComparisonFailure {
//    try {
//      Assert.assertArrayEquals(message, expecteds, actuals);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertArrayEquals(short[] expecteds, short[] actuals) {
//    try {
//      Assert.assertArrayEquals(expecteds, actuals);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertArrayEquals(long[] expecteds, long[] actuals) {
//    try {
//      Assert.assertArrayEquals(expecteds, actuals);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertNotNull(Object object) {
//    try {
//      Assert.assertNotNull(object);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertEquals(Object expected, Object actual) {
//    try {
//      Assert.assertEquals(expected, actual);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertEquals(String message, Object expected, Object actual) {
//    try {
//      Assert.assertEquals(message, expected, actual);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertTrue(boolean condition) {
//    try {
//      Assert.assertTrue(condition);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertArrayEquals(Object[] expecteds, Object[] actuals) {
//    try {
//      Assert.assertArrayEquals(expecteds, actuals);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertNotNull(String message, Object object) {
//    try {
//      Assert.assertNotNull(message, object);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertEquals(String message, double expected, double actual, double delta) {
//    try {
//      Assert.assertEquals(message, expected, actual, delta);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void fail() {
//    try {
//      Assert.fail();
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertSame(Object expected, Object actual) {
//    try {
//      Assert.assertSame(expected, actual);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertEquals(String message, long expected, long actual) {
//    try {
//      Assert.assertEquals(message, expected, actual);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertArrayEquals(String message, byte[] expecteds, byte[] actuals) throws ArrayComparisonFailure {
//    try {
//      Assert.assertArrayEquals(message, expecteds, actuals);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertArrayEquals(String message, long[] expecteds, long[] actuals) throws ArrayComparisonFailure {
//    try {
//      Assert.assertArrayEquals(message, expecteds, actuals);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertEquals(double expected, double actual, double delta) {
//    try {
//      Assert.assertEquals(expected, actual, delta);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static <T> void assertThat(T actual, Matcher<T> matcher) {
//    try {
//      Assert.assertThat(actual, matcher);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  @Deprecated
//  public static void assertEquals(String message, Object[] expecteds, Object[] actuals) {
//    try {
//      Assert.assertEquals(message, expecteds, actuals);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  @Deprecated
//  public static void assertEquals(Object[] expecteds, Object[] actuals) {
//    try {
//      Assert.assertEquals(expecteds, actuals);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertNotSame(String message, Object unexpected, Object actual) {
//    try {
//      Assert.assertNotSame(message, unexpected, actual);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static <T> void assertThat(String reason, T actual, Matcher<T> matcher) {
//    try {
//      Assert.assertThat(reason, actual, matcher);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertArrayEquals(float[] expecteds, float[] actuals, float delta) {
//    try {
//      Assert.assertArrayEquals(expecteds, actuals, delta);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertNotSame(Object unexpected, Object actual) {
//    try {
//      Assert.assertNotSame(unexpected, actual);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertArrayEquals(byte[] expecteds, byte[] actuals) {
//    try {
//      Assert.assertArrayEquals(expecteds, actuals);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertArrayEquals(char[] expecteds, char[] actuals) {
//    try {
//      Assert.assertArrayEquals(expecteds, actuals);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertArrayEquals(double[] expecteds, double[] actuals, double delta) {
//    try {
//      Assert.assertArrayEquals(expecteds, actuals, delta);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertArrayEquals(int[] expecteds, int[] actuals) {
//    try {
//      Assert.assertArrayEquals(expecteds, actuals);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  @Deprecated
//  public static void assertEquals(double expected, double actual) {
//    try {
//      Assert.assertEquals(expected, actual);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }
//
//  public static void assertArrayEquals(String message, int[] expecteds, int[] actuals) throws ArrayComparisonFailure {
//    try {
//      Assert.assertArrayEquals(message, expecteds, actuals);
//    } catch (AssertionError e) {
//      handleThrowable(e);
//    }
//  }



}

