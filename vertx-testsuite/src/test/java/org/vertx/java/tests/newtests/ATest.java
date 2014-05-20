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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ATest {

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

  public void assertBlock(Runnable runner) {
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

}

