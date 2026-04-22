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

import io.vertx.core.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.test.fakemetrics.FakeMetricsFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runners.model.InitializationError;

import static org.junit.Assert.*;

/**
 * Test the behavior of stateless vertx test.
 */
public class RunnerTest {

  private static final RuntimeException RUNTIME_EXCEPTION = new RuntimeException();

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
    assertSame(RUNTIME_EXCEPTION, result.getFailures().get(0).getException());
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

  @Test
  public void testInjectVertxInTestMethod() {
    InjectVertxInTestMethod.vertx = null;
    InjectVertxInTestMethod.undeployed = false;
    Result result = runTest(InjectVertxInTestMethod.class);
    assertEquals(0, result.getFailureCount());
    assertNotNull(InjectVertxInTestMethod.vertx);
    assertTrue(InjectVertxInTestMethod.undeployed);
  }

  @RunWith(VertxRunner.class)
  public static class InjectVertxInTestMethod {

    static Vertx vertx;
    static boolean undeployed;

    @Test
    public void test(Vertx vertx) {
      InjectVertxInTestMethod.vertx = vertx;
      vertx.deployVerticle(new Deployable() {
        @Override
        public Future<?> deploy(Context context) {
          return Future.succeededFuture();
        }
        @Override
        public Future<?> undeploy(Context context) throws Exception {
          undeployed = true;
          return Deployable.super.undeploy(context);
        }
      }).await();
    }

    @After
    public void after() {
      assertTrue(undeployed);
    }
  }

  @Test
  public void testInjectVertxInBeforeMethod() {
    InjectVertxInBeforeMethod.vertx = null;
    InjectVertxInBeforeMethod.undeployed = false;
    Result result = runTest(InjectVertxInBeforeMethod.class);
    assertEquals(0, result.getFailureCount());
    assertNotNull(InjectVertxInTestMethod.vertx);
    assertTrue(InjectVertxInTestMethod.undeployed);
  }

  @RunWith(VertxRunner.class)
  public static class InjectVertxInBeforeMethod {

    static Vertx vertx;
    static boolean undeployed;

    @Before
    public void before(Vertx vertx) {
      InjectVertxInBeforeMethod.vertx = vertx;
      vertx.deployVerticle(new Deployable() {
        @Override
        public Future<?> deploy(Context context) {
          return Future.succeededFuture();
        }
        @Override
        public Future<?> undeploy(Context context) throws Exception {
          undeployed = true;
          return Deployable.super.undeploy(context);
        }
      }).await();
    }

    @Test
    public void test() {
      assertFalse(undeployed);
    }

    @After
    public void after() {
      assertFalse(undeployed);
    }
  }

  @Test
  public void testReportVertxFailure() {
    Result result = runTest(ReportVertxFailure.class);
    assertEquals(1, result.getFailureCount());
    assertSame(RUNTIME_EXCEPTION, result.getFailures().get(0).getException());
  }

  @RunWith(VertxRunner.class)
  public static class ReportVertxFailure {

    @Test
    public void test(Vertx vertx) {
      ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
      context.reportException(RUNTIME_EXCEPTION);
    }
  }

  @Test
  public void testReportedFailureFailsCheckpoint() {
    Result result = runTest(ReportedFailureCancelCheckpoint.class);
    assertEquals(1, result.getFailureCount());
    assertSame(RUNTIME_EXCEPTION, result.getFailures().get(0).getException());
    Checkpoint checkpoint = ReportedFailureCancelCheckpoint.checkpoint;
    assertNotNull(checkpoint);
    try {
      checkpoint.awaitSuccess();
      fail();
    } catch (Exception e) {
      assertSame(RUNTIME_EXCEPTION, e);
    }
  }

  @RunWith(VertxRunner.class)
  public static class ReportedFailureCancelCheckpoint {

    static Checkpoint checkpoint;

    @Test
    public void test(Vertx vertx, Checkpoint checkpoint) {
      ReportedFailureCancelCheckpoint.checkpoint = checkpoint;
      ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
      context.reportException(RUNTIME_EXCEPTION);
    }
  }

  @Test
  public void testVertxProvider() {
    InjectProvidedInstance.metricsEnabled = false;
    Result result = runTest(InjectProvidedInstance.class);
    assertEquals(0, result.getFailureCount());
    assertTrue(InjectProvidedInstance.metricsEnabled);
  }

  public static class ConfiguredVertxProvider implements VertxProvider {
    @Override
    public Vertx call() {
      return Vertx.builder()
        .with(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)))
        .withMetrics(new FakeMetricsFactory())
        .build();
    }
  }

  @RunWith(VertxRunner.class)
  public static class InjectProvidedInstance {

    public static boolean metricsEnabled;

    @Test
    public void test(@ProvidedBy(ConfiguredVertxProvider.class) Vertx vertx) {
      metricsEnabled = vertx.isMetricsEnabled();
    }
  }
}
