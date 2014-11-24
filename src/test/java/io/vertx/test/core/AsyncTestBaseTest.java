/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import org.junit.ComparisonFailure;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AsyncTestBaseTest extends AsyncTestBase {

  private ExecutorService executor;

  public void setUp() throws Exception {
    super.setUp();
    disableThreadChecks();
    executor = Executors.newFixedThreadPool(10);
  }

  protected void tearDown() throws Exception {
    executor.shutdownNow();
    super.tearDown();
  }

  @Test
  public void testAssertionFailedFromOtherThread() {
    executor.execute(() -> {
      assertEquals("foo", "bar");
      testComplete();
    });
    try {
      await();
    } catch (ComparisonFailure error) {
      assertTrue(error.getMessage().startsWith("expected:"));
    }
  }

  @Test
  public void testAssertionFailedFromOtherThreadAwaitBeforeAssertAndTestComplete() {
    executor.execute(() -> {
      //Pause to make sure await() is called before assertion and testComplete
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        fail(e.getMessage());
      }
      assertEquals("foo", "bar");
      testComplete();
    });
    try {
      await();
    } catch (ComparisonFailure error) {
      assertTrue(error.getMessage().startsWith("expected:"));
    }
  }

  @Test
  public void testAssertionFailedFromOtherThreadForgotToCallAwait() throws Exception {
    executor.execute(() -> {
      assertEquals("foo", "bar");
      testComplete();
    });
    Thread.sleep(500);
    try {
      super.afterAsyncTestBase();
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // OK
    } finally {
      // Cancel the error condition
      clearThrown();
    }
  }

  @Test
  public void testAssertionFailedFromMainThread() {
    try {
      assertEquals("foo", "bar");
    } catch (ComparisonFailure error) {
      assertTrue(error.getMessage().startsWith("expected:"));
    }
    testComplete();
  }

  @Test
  public void testAssertionPassedFromOtherThread() {
    executor.execute(() -> {
      assertEquals("foo", "foo");
      testComplete();
    });
    await();
  }

  @Test
  public void testAssertionPassedFromMainThread() {
    assertEquals("foo", "foo");
    testComplete();
    await();
  }

  @Test
  public void testTimeout() {
    long timeout = 5000;
    long start = System.currentTimeMillis();
    try {
      await(timeout, TimeUnit.MILLISECONDS);
    } catch (IllegalStateException error) {
      long now = System.currentTimeMillis();
      assertTrue(error.getMessage().startsWith("Timed out in waiting for test complete"));
      long delay = now - start;
      assertTrue(delay >= timeout);
      assertTrue(delay < timeout * 1.5);
    }
  }

// Commented this test as default timeout is now too large
//  @Test
//  public void testTimeoutDefault() {
//    long start = System.currentTimeMillis();
//    try {
//      await();
//    } catch (IllegalStateException error) {
//      long now = System.currentTimeMillis();
//      assertTrue(error.getMessage().startsWith("Timed out in waiting for test complete"));
//      long delay = now - start;
//      long defaultTimeout = 10000;
//      assertTrue(delay >= defaultTimeout);
//      assertTrue(delay < defaultTimeout * 1.5);
//    }
//  }


  @Test
  public void testFailFromOtherThread() {
    String msg = "too many aardvarks!";
    executor.execute(() -> {
      fail(msg);
      testComplete();
    });
    try {
      await();
    } catch (AssertionError error) {
      assertTrue(error.getMessage().equals(msg));
    }
  }

  @Test
  public void testSuccessfulCompletion() {
    executor.execute(() -> {
      assertEquals("foo", "foo");
      assertFalse(false);
      testComplete();
    });
    await();
  }

  @Test
  public void testTestCompleteCalledMultipleTimes() {
    executor.execute(() -> {
      assertEquals("foo", "foo");
      testComplete();
      try {
        testComplete();
      } catch (IllegalStateException e) {
        //OK
      }
    });
    await();
  }

  @Test
  public void testAwaitCalledMultipleTimes() {
    executor.execute(() -> {
      assertEquals("foo", "foo");
      testComplete();
    });
    await();
    try {
      await();
    } catch (IllegalStateException e) {
      //OK
    }
  }

  @Test
  public void testNoAssertionsNoTestComplete() {
    // Deliberately empty test
  }

  @Test
  public void testNoAssertionsTestComplete() {
    testComplete();
  }

  @Test
  public void testAssertionOKTestComplete() {
    assertEquals("foo", "foo");
    testComplete();
  }

  @Test
  public void testAssertionFailedFromMainThreadWithNoTestComplete() {
    try {
      assertEquals("foo", "bar");
    } catch (AssertionError e) {
      // OK
      testComplete();
      try {
        super.afterAsyncTestBase();
      } catch (IllegalStateException e2) {
        fail("Should not throw exception");
      } finally {
        // Cancel the error condition
        clearThrown();
      }
    }
  }

  @Test
  public void waitForMultiple() {
    int toWaitFor = 10;
    waitFor(10);
    AtomicInteger cnt = new AtomicInteger();
    for (int i = 0; i < toWaitFor; i++) {
      executor.execute(() -> {
        cnt.incrementAndGet();
        complete();
      });
    }
    await();
    assertEquals(toWaitFor, cnt.get());
  }

  @Test
  public void increaseToWait() {
    int toWaitFor = 10;
    waitFor(3);
    complete();
    complete();
    waitForMore(9);
    AtomicInteger cnt = new AtomicInteger();
    for (int i = 0; i < toWaitFor; i++) {
      executor.execute(() -> {
        cnt.incrementAndGet();
        complete();
      });
    }
    await();
    assertEquals(toWaitFor, cnt.get());
  }

}
