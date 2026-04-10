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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * Test the behavior of stateless vertx test.
 */
public class ReportModeVertxTest {

  private static final RuntimeException RUNTIME_EXCEPTION = new RuntimeException();
  private static final String FAILURE_MSG = "the-failure";

  private Result runTest(Class<?> testClass) {
    try {
      return new JUnitCore().run(new BlockJUnit4ClassRunner(testClass));
    } catch (InitializationError initializationError) {
      throw new AssertionError(initializationError);
    }
  }

  @Test
  public void testStatelessFromRunOnContext() {
    Result result = runTest(Stateless.FromRunOnContext.Throw.class);
    assertEquals(1, result.getFailureCount());
    assertEquals(RUNTIME_EXCEPTION, result.getFailures().get(0).getException());

    result = runTest(Stateless.FromRunOnContext.Fail.class);
    assertEquals(1, result.getFailureCount());
    assertEquals(AssertionError.class, result.getFailures().get(0).getException().getClass());
    assertEquals(FAILURE_MSG, result.getFailures().get(0).getException().getMessage());
  }

  @Test
  public void testStatelessThrowFromTestThread() {
    Result result = runTest(Stateless.FromTestThread.Throw.class);
    assertEquals(1, result.getFailureCount());
    assertEquals(RUNTIME_EXCEPTION, result.getFailures().get(0).getException());

    result = runTest(Stateless.FromTestThread.Fail.class);
    assertEquals(1, result.getFailureCount());
    assertEquals(AssertionError.class, result.getFailures().get(0).getException().getClass());
    assertEquals(FAILURE_MSG, result.getFailures().get(0).getException().getMessage());
  }

  @Test
  public void testStatelessFromNode() {
    Result result = runTest(Stateless.FromNode.class);
    assertEquals(1, result.getFailureCount());
    assertEquals(RUNTIME_EXCEPTION, result.getFailures().get(0).getException());
  }

  @Test
  public void testForbidden() {
    Result result = runTest(Forbidden.class);
    assertEquals(1, result.getFailureCount());
    assertThat(result.getFailures().get(0).getException()).isInstanceOf(AssertionError.class);
  }

  public static class Forbidden extends VertxTestBase {

    public Forbidden() {
      super(ReportMode.FORBIDDEN);
    }

    @Test
    public void testMethod() throws Exception {
      assertTrue(true);
      await();
    }
  }

  public abstract static class Stateless {

    public abstract static class FromRunOnContext extends VertxTestBase {

      public FromRunOnContext() {
        super(ReportMode.STATELESS);
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

    public abstract static class FromTestThread extends VertxTestBase {

      public FromTestThread() {
        super(ReportMode.STATELESS);
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

    public static class FromNode extends VertxTestBase {

      public FromNode() {
        super(ReportMode.STATELESS);
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
  }
}
