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
import org.junit.runner.RunWith;
import org.junit.runners.model.InitializationError;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.*;

/**
 * Test the behavior of stateless vertx test.
 */
public class RunnerTest {

  private static final RuntimeException RUNTIME_EXCEPTION = new RuntimeException();
  private static final String FAILURE_MSG = "the-failure";

  private Result runTest(Class<?> testClass) {
    try {
      return new JUnitCore().run(new VertxRunner(testClass));
    } catch (InitializationError initializationError) {
      throw new AssertionError(initializationError);
    }
  }

  @Test
  public void testSucceedCheckpoint() {
    Result result = runTest(SucceedCheckpoint.class);
    assertEquals(0, result.getFailureCount());
  }

  @RunWith(VertxRunner.class)
  public static class SucceedCheckpoint {
    @Test
    public void test(Checkpoint checkpoint) {
      checkpoint.succeed();
    }
  }

  @Test
  public void testFailCheckpoint() {
    Result result = runTest(FailCheckpoint.class);
    assertEquals(1, result.getFailureCount());
    assertSame(RUNTIME_EXCEPTION, result.getFailures().get(0).getException().getCause());
  }

  @RunWith(VertxRunner.class)
  public static class FailCheckpoint {
    @Test
    public void test(Checkpoint checkpoint) {
      checkpoint.fail(RUNTIME_EXCEPTION);
    }
  }

  @Test
  public void testSucceedCheckpointAsync() {
    SucceedCheckpointAsync.executed = false;
    Result result = runTest(SucceedCheckpointAsync.class);
    assertEquals(0, result.getFailureCount());
    assertTrue(SucceedCheckpointAsync.executed);
  }

  @RunWith(VertxRunner.class)
  public static class SucceedCheckpointAsync {
    static volatile boolean executed;
    @Test
    public void test(Checkpoint checkpoint) {
      new Thread(() -> {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          return;
        }
        executed = true;
        checkpoint.succeed();
      }).start();
    }
  }
}
