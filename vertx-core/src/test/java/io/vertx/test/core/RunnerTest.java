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
import io.vertx.core.internal.FutureInternal;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.test.fakemetrics.FakeMetricsFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.junit.runners.model.InitializationError;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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
  public void testFutureFails() {
    Result result = runTest(FutureFails.class);
    assertEquals(0, result.getFailureCount());
  }

  @RunWith(VertxRunner.class)
  public static class FutureFails {
    @Test
    public void test(Checkpoint checkpoint) {
      FutureAssert.assertThat(Future.failedFuture(RUNTIME_EXCEPTION)).isFailure().verify(checkpoint);
    }
  }

  @Test
  public void testFutureFailsUnexpectedSuccess() {
    Result result = runTest(FutureFailsUnexpectedSuccess.class);
    assertEquals(1, result.getFailureCount());
    Throwable failure = result.getFailures().get(0).getException();
    assertEquals(AssertionError.class, failure.getClass());
    assertEquals("Unexpected success: foo", failure.getMessage());
  }

  @RunWith(VertxRunner.class)
  public static class FutureFailsUnexpectedSuccess {
    @Test
    public void test(Checkpoint checkpoint) {
      FutureAssert.assertThat(Future.succeededFuture("foo")).isFailure().verify(checkpoint);
    }
  }

  @Test
  public void testFutureFailsReportsOnContext() {
    Result result = runTest(FutureFailsReportsOnContext.class);
    assertEquals(2, result.getFailureCount());
    assertSame(RUNTIME_EXCEPTION, result.getFailures().get(0).getException());
    assertSame(RUNTIME_EXCEPTION, result.getFailures().get(1).getException());
  }

  @RunWith(VertxRunner.class)
  public static class FutureFailsReportsOnContext {
    @Test
    public void testFailure(Vertx vertx, Checkpoint checkpoint) {
      ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
      test(context, context.failedFuture("boom"));
    }
    @Test
    public void testUnexpectedSuccess(Vertx vertx, Checkpoint checkpoint) {
      ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
      test(context, context.succeededFuture("foo"));
    }
    private void test(ContextInternal context, Future<?> future) {
      Future<Throwable> fails = FutureAssert.assertThat(future).isFailure();
      assertSame(context, ((FutureInternal<Throwable>) fails).context());
      fails.onComplete(ar -> {
        throw RUNTIME_EXCEPTION;
      });
    }
  }

  @Test
  public void testFutureAssertCallbacksOnContext() {
    Result result = runTest(FutureAssertCallbacksOnContext.class);
    assertEquals(0, result.getFailureCount());
  }

  @RunWith(VertxRunner.class)
  public static class FutureAssertCallbacksOnContext {
    @Test
    public void testFailure(Vertx vertx) {
      ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
      Promise<String> promise = context.promise();
      Future<RuntimeException> chain = FutureAssert.assertThat(promise.future())
        .isFailure()
        .peek(err -> assertSame(context, Vertx.currentContext()))
        .asInstanceOf(RuntimeException.class)
        .peek(err -> assertSame(context, Vertx.currentContext()))
        .andThen(ar -> assertSame(context, Vertx.currentContext()));
      promise.fail(RUNTIME_EXCEPTION);
      assertSame(RUNTIME_EXCEPTION, chain.await());
    }
    @Test
    public void testSuccess(Vertx vertx) {
      ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
      Promise<String> promise = context.promise();
      Future<String> chain = FutureAssert.assertThat(promise.future())
        .compose(v -> {
          assertSame(context, Vertx.currentContext());
          return Future.succeededFuture(v + "bar");
        })
        .peek(v -> assertSame(context, Vertx.currentContext()))
        .andThen(ar -> assertSame(context, Vertx.currentContext()));
      promise.complete("foo");
      assertEquals("foobar", chain.await());
    }
  }

  @Test
  public void testFutureAssertInPlainFutureMapper() {
    Result result = runTest(FutureAssertInPlainFutureMapper.class);
    assertEquals(0, result.getFailureCount());
  }

  @RunWith(VertxRunner.class)
  public static class FutureAssertInPlainFutureMapper {
    @Test
    public void test() {
      Throwable failure = Future.succeededFuture("foo")
        .compose(v -> FutureAssert.assertThat(Future.failedFuture(RUNTIME_EXCEPTION)).isFailure())
        .transform(ar -> FutureAssert.assertThat(Future.succeededFuture(ar.result())))
        .await();
      assertSame(RUNTIME_EXCEPTION, failure);
    }
  }

  @Test
  public void testFutureAssertChain() {
    Result result = runTest(FutureAssertChain.class);
    assertEquals(0, result.getFailureCount());
  }

  @RunWith(VertxRunner.class)
  public static class FutureAssertChain {
    @Test
    public void test(Checkpoint checkpoint) {
      FutureAssert.assertThat(Future.succeededFuture("foo"))
        .compose(v -> Future.failedFuture(RUNTIME_EXCEPTION))
        .isFailure()
        .compose(err -> Future.succeededFuture("bar"))
        .compose(v -> Future.failedFuture(RUNTIME_EXCEPTION))
        .isFailure()
        .compose(err -> FutureAssert.assertThat(Future.failedFuture(RUNTIME_EXCEPTION)).isFailure())
        .transform(ar -> FutureAssert.assertThat(Future.failedFuture(RUNTIME_EXCEPTION)))
        .isFailure()
        .peek(err -> assertSame(RUNTIME_EXCEPTION, err))
        .asInstanceOf(RuntimeException.class)
        .verify(checkpoint);
    }
  }

  @Test
  public void testFutureAsInstanceOfFails() {
    Result result = runTest(FutureAsInstanceOfFails.class);
    assertEquals(1, result.getFailureCount());
    Throwable failure = result.getFailures().get(0).getException();
    assertEquals(AssertionError.class, failure.getClass());
    assertEquals("Expecting foo to be an instance of java.lang.Integer but was instance of java.lang.String", failure.getMessage());
  }

  @RunWith(VertxRunner.class)
  public static class FutureAsInstanceOfFails {
    @Test
    public void test(Checkpoint checkpoint) {
      FutureAssert.assertThat(Future.succeededFuture("foo"))
        .asInstanceOf(Integer.class)
        .verify(checkpoint);
    }
  }

  @Test
  public void testFuturePeekFails() {
    Result result = runTest(FuturePeekFails.class);
    assertEquals(2, result.getFailureCount());
    Set<Throwable> failures = new HashSet<>();
    result.getFailures().forEach(failure -> failures.add(failure.getException()));
    assertEquals(new HashSet<>(Arrays.asList(FuturePeekFails.ASSERTION_ERROR, RUNTIME_EXCEPTION)), failures);
  }

  @RunWith(VertxRunner.class)
  public static class FuturePeekFails {
    static final AssertionError ASSERTION_ERROR = new AssertionError();
    @Test
    public void testAssertionError(Checkpoint checkpoint) {
      FutureAssert.assertThat(Future.succeededFuture("foo"))
        .peek(v -> {
          throw ASSERTION_ERROR;
        })
        .verify(checkpoint);
    }
    @Test
    public void testRuntimeException(Checkpoint checkpoint) {
      FutureAssert.assertThat(Future.succeededFuture("foo"))
        .peek(v -> {
          throw RUNTIME_EXCEPTION;
        })
        .verify(checkpoint);
    }
  }

  @Test
  public void testFutureAssertOverridesFutureOperators() throws Exception {
    for (java.lang.reflect.Method method : Future.class.getMethods()) {
      boolean onCompleteCompletable = method.getName().equals("onComplete") && method.getParameterTypes()[0] == Completable.class;
      if (!java.lang.reflect.Modifier.isStatic(method.getModifiers()) && method.getReturnType() == Future.class && !onCompleteCompletable) {
        java.lang.reflect.Method override = FutureAssert.class.getMethod(method.getName(), method.getParameterTypes());
        assertEquals(override.toString(), FutureAssert.class, override.getReturnType());
      }
    }
  }

  @Test
  public void testFutureAssertVerify() {
    Result result = runTest(FutureAssertVerify.class);
    assertEquals(1, result.getFailureCount());
    assertSame(RUNTIME_EXCEPTION, result.getFailures().get(0).getException());
  }

  @FixMethodOrder(MethodSorters.NAME_ASCENDING)
  @RunWith(VertxRunner.class)
  public static class FutureAssertVerify {
    @Test
    public void testFailure(Checkpoint checkpoint) {
      FutureAssert.assertThat(Future.failedFuture(RUNTIME_EXCEPTION)).verify(checkpoint);
    }
    @Test
    public void testSuccess(Checkpoint checkpoint) {
      FutureAssert.assertThat(Future.succeededFuture("foo")).verify(checkpoint);
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
  public void testStaticVertxProvider() {
    InjectStaticProvidedInstance.metricsEnabled = false;
    Result result = runTest(InjectStaticProvidedInstance.class);
    assertEquals(0, result.getFailureCount());
    assertTrue(InjectStaticProvidedInstance.metricsEnabled);
  }

  public static class StaticConfiguredVertxProvider implements VertxProvider {
    @Override
    public Vertx call() {
      return Vertx.builder()
        .with(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)))
        .withMetrics(new FakeMetricsFactory())
        .build();
    }
  }

  @RunWith(VertxRunner.class)
  public static class InjectStaticProvidedInstance {

    public static boolean metricsEnabled;

    @Test
    public void test(@ProvidedBy(StaticConfiguredVertxProvider.class) Vertx vertx) {
      metricsEnabled = vertx.isMetricsEnabled();
    }
  }

  @Test
  public void testVertxProvider() {
    Result result = runTest(InjectProvidedInstance.class);
    assertEquals(0, result.getFailureCount());
  }

  @RunWith(VertxRunner.class)
  public static class InjectProvidedInstance {

    public class ConfiguredVertxProvider implements VertxProvider {

      @Override
      public Vertx call() {
        Vertx vertx = Vertx.builder()
          .with(new VertxOptions().setMetricsOptions(new MetricsOptions().setEnabled(true)))
          .withMetrics(new FakeMetricsFactory())
          .build();
        InjectProvidedInstance.this.vertx = vertx;
        return vertx;
      }
    }

    private Vertx vertx;

    @Test
    public void test(@ProvidedBy(ConfiguredVertxProvider.class) Vertx vertx) {
      assertSame(vertx, this.vertx);
    }
  }
}
