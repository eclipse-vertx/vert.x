package org.vertx.java.tests.newtests;

import org.junit.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

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
public class JUnitAsyncHelperTest {

  ExecutorService executor;
  JUnitAsyncHelper atest;

  @Before
  public void before() {
    executor = Executors.newFixedThreadPool(10);
    atest = new JUnitAsyncHelper();
  }

  @After
  public void after() {
    assertTrue(atest.isAwaitCalled()); // Make sure that user actually called await in the test or might get a false +ve
    executor.shutdownNow();
  }

  @Test
  public void testAssertionFailedFromOtherThread() {
    executor.execute(() -> {
      atest.doAssert(() -> {
        assertEquals("foo", "bar");
      });
      atest.testComplete();
    });
    try {
      atest.await();
    } catch (ComparisonFailure error) {
      assertTrue(error.getMessage().startsWith("expected:"));
    }
  }

  @Test
  public void testAssertionFailedFromMainThread() {

    atest.doAssert(() -> {
      assertEquals("foo", "bar");
    });
    atest.testComplete();

    try {
      atest.await();
    } catch (ComparisonFailure error) {
      assertTrue(error.getMessage().startsWith("expected:"));
    }
  }

  @Test
  public void testAssertionPassedFromOtherThread() {
    executor.execute(() -> {
      atest.doAssert(() -> {
        assertEquals("foo", "foo");
      });
      atest.testComplete();
    });
    atest.await();
  }

  @Test
  public void testAssertionPassedFromMainThread() {
    atest.doAssert(() -> {
      assertEquals("foo", "foo");
    });
    atest.testComplete();
    atest.await();
  }

  @Test
  public void testTimeout() {
    long timeout = 5000;
    long start = System.currentTimeMillis();
    try {
      atest.await(timeout, TimeUnit.MILLISECONDS);
    } catch (IllegalStateException error) {
      long now = System.currentTimeMillis();
      assertTrue(error.getMessage().startsWith("Timed out in waiting for test complete"));
      long delay = now - start;
      assertTrue(delay >= timeout);
      assertTrue(delay < timeout * 1.5);
    }
  }

  @Test
  public void testTimeoutDefault() {
    long start = System.currentTimeMillis();
    try {
      atest.await();
    } catch (IllegalStateException error) {
      long now = System.currentTimeMillis();
      assertTrue(error.getMessage().startsWith("Timed out in waiting for test complete"));
      long delay = now - start;
      long defaultTimeout = 10000;
      assertTrue(delay >= defaultTimeout);
      assertTrue(delay < defaultTimeout * 1.5);
    }
  }

  @Test
  public void testFailFromOtherThread() {
    String msg = "too many aardvarks!";
    executor.execute(() -> {
      atest.doAssert(() -> {
        fail(msg);
      });
      atest.testComplete();
    });
    try {
      atest.await();
    } catch (AssertionError error) {
      assertTrue(error.getMessage().equals(msg));
    }
  }

  @Test
  public void testSuccessfulCompletion() {
    executor.execute(() -> {
      atest.doAssert(() -> {
        assertEquals("foo", "foo");
        assertFalse(false);
      });
      atest.testComplete();
    });
    atest.await();
  }

  @Test
  public void testTestCompleteCalledMultipleTimes() {
    executor.execute(() -> {
      atest.doAssert(() -> {
        assertEquals("foo", "foo");
      });
      atest.testComplete();
      try {
        atest.testComplete();
      } catch (IllegalStateException e) {
        //OK
      }
    });
    atest.await();
  }

  @Test
  public void testAwaitCalledMultipleTimes() {
    executor.execute(() -> {
      atest.doAssert(() -> {
        assertEquals("foo", "foo");
      });
      atest.testComplete();
    });
    atest.await();
    try {
      atest.await();
    } catch (IllegalStateException e) {
      //OK
    }
  }

  @Test
  public void testRuntimeExceptionThrownFromAssertBlock() {
    String msg = "foo bar";
    executor.execute(() -> {
      atest.doAssert(() -> {
        throw new RuntimeException(msg);
      });
      atest.testComplete();
    });
    try {
      atest.await();
    } catch (RuntimeException e) {
      assertEquals(msg, e.getMessage());
    }
  }

  @Test
  public void testGeneralErrorThrownFromAssertBlock() {
    String msg = "wibble quux";
    executor.execute(() -> {
      atest.doAssert(() -> {
        throw new Error(msg);
      });
      atest.testComplete();
    });
    try {
      atest.await();
    } catch (Error e) {
      assertEquals(msg, e.getMessage());
    }
  }

}
