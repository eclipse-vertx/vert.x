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
package io.vertx.test.core;

import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 * Test the behavior of stateless vertx test.
 */
public class StatelessVertxTest {

  private static final RuntimeException RUNTIME_EXCEPTION = new RuntimeException();
  private static final String FAILURE_MSG = "the-failure";

  private Result runTest(Class<?> testClass) {
    try {
      return new JUnitCore().run(new BlockJUnit4ClassRunner(testClass));
    } catch (InitializationError initializationError) {
      throw new AssertionError(initializationError);
    }
  }

  public abstract static class FromRunOnContext extends VertxTestBase {

    public FromRunOnContext() {
      super(true);
    }

    @Test
    public void testMethod() throws Exception {
      CountDownLatch latch = new CountDownLatch(1);
      vertx.runOnContext(v1 -> {
        vertx.runOnContext(v2 -> {
          latch.countDown();
        });
        implementation();
      });
      assertTrue(latch.await(10, TimeUnit.SECONDS));
      await();
    }

    abstract void implementation();

    public static class Throw extends FromRunOnContext {
      @Override
      void implementation() {
        throw RUNTIME_EXCEPTION;
      }
    }

    public static class Fail extends FromRunOnContext {
      @Override
      void implementation() {
        fail(FAILURE_MSG);
      }
    }
  }

  @Test
  public void testFromRunOnContext() {
    Result result = runTest(FromRunOnContext.Throw.class);
    assertEquals(1, result.getFailureCount());
    assertEquals(RUNTIME_EXCEPTION, result.getFailures().get(0).getException());

    result = runTest(FromRunOnContext.Fail.class);
    assertEquals(1, result.getFailureCount());
    assertEquals(AssertionError.class, result.getFailures().get(0).getException().getClass());
    assertEquals(FAILURE_MSG, result.getFailures().get(0).getException().getMessage());
  }

  public abstract static class FromTestThread extends VertxTestBase {

    public FromTestThread() {
      super(true);
    }

    @Test
    public void testMethod() {
      implementation();
    }

    abstract void implementation();

    public static class Throw extends FromTestThread {
      @Override
      void implementation() {
        throw RUNTIME_EXCEPTION;
      }
    }

    public static class Fail extends FromRunOnContext {
      @Override
      void implementation() {
        fail(FAILURE_MSG);
      }
    }
  }

  @Test
  public void testThrowFromTestThread() {
    Result result = runTest(FromTestThread.Throw.class);
    assertEquals(1, result.getFailureCount());
    assertEquals(RUNTIME_EXCEPTION, result.getFailures().get(0).getException());

    result = runTest(FromTestThread.Fail.class);
    assertEquals(1, result.getFailureCount());
    assertEquals(AssertionError.class, result.getFailures().get(0).getException().getClass());
    assertEquals(FAILURE_MSG, result.getFailures().get(0).getException().getMessage());
  }

  public static class FromNode extends VertxTestBase {

    public FromNode() {
      super(true);
    }

    @Test
    public void testMethod() throws Exception {
      startNodes(1);
      CountDownLatch latch = new CountDownLatch(1);
      vertices[0].runOnContext(v1 -> {
        vertices[0].runOnContext(v2 -> {
          latch.countDown();
        });
        throw RUNTIME_EXCEPTION;
      });
      assertTrue(latch.await(10, TimeUnit.SECONDS));
      await();
    }
  }

  @Test
  public void testFromNode() {
    Result result = runTest(FromNode.class);
    assertEquals(1, result.getFailureCount());
    assertEquals(RUNTIME_EXCEPTION, result.getFailures().get(0).getException());
  }
}
