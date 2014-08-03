package io.vertx.test.core;

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

import org.junit.After;
import org.junit.Before;
import org.junit.ComparisonFailure;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AsyncTestBaseTest extends AsyncTestBase {

  private ExecutorService executor;

  @Before
  public void before() {
    disableThreadChecks();
    executor = Executors.newFixedThreadPool(10);
  }

  @After
  public void after() throws Exception {
    executor.shutdownNow();
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

// Removed this test as default timeout is now too large
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


}
