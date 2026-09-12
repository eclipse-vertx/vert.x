/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.vertx;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.VertxException;
import io.vertx.core.internal.ServiceResource;
import io.vertx.core.internal.ContextInternal;
import io.vertx.test.core.*;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class ServiceResourceTest extends VertxTestBase2 {

  static class TestResource extends ServiceResource<Void, Void> {

    public boolean isDone() {
      return !super.hasPendingTasks();
    }

  }

  private ContextInternal context;

  @Before
  public void before() {
    vertx.deployVerticle(ctx -> {
      context = (ContextInternal) ctx;
      return Future.succeededFuture();
    }).await();

  }

  @Test
  public void testStartSuccess(Checkpoint checkpoint) {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    TestResource resource = new TestResource() {
      @Override
      protected Future<Void> startImpl(ContextInternal ctx, Void args) {
        assertSame(context, ctx);
        return super.startImpl(ctx, args);
      }
    };
    resource.start(context, null).onComplete(TestUtils.onSuccess(v -> {
      checkpoint.succeed();
    }));
  }

  @Test
  public void testStartFailure(Checkpoint checkpoint) {
    Throwable expected = new Throwable();
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    TestResource resource = new TestResource() {
      @Override
      protected Future<Void> startImpl(ContextInternal context, Void args) {
        return context.failedFuture(expected);
      }
    };
    resource.start(context, null).onComplete(TestUtils.onFailure2(failure -> {
      assertSame(expected, failure);
      checkpoint.succeed();
    }));
  }

  @Test
  public void testStartStarted() {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    TestResource resource = new TestResource() {
    };
    resource.start(context, null).await();
    try {
      resource.start(context, null).await();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testStartStarting() {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    Promise<Void> continuation = context.promise();
    TestResource resource = new TestResource() {
      @Override
      protected Future<Void> startImpl(ContextInternal context, Void args) {
        return continuation.future();
      }
    };
    resource.start(context, null);
    Future<Void> start2 = resource.start(context, null);
    assertFalse(start2.isComplete());
    continuation.succeed();
    try {
      start2.await();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testCloseHook() {
    AtomicInteger stop = new AtomicInteger();
    TestResource resource = new TestResource() {
      @Override
      protected Future<Void> stopImpl(ContextInternal ctx, Void args, Duration timeout) {
        assertSame(context, ctx);
        stop.incrementAndGet();
        return ctx.succeededFuture();
      }
    };
    resource.start(context, null).await();
    vertx.undeploy(context.deploymentID()).await();
    assertEquals(1, stop.get());
  }

  @Test
  public void startServiceAfterUndeploy(Checkpoint checkpoint) {
    AtomicInteger stop = new AtomicInteger();
    Promise<Void> continuation = context.promise();
    TestResource resource = new TestResource() {
      @Override
      protected Future<Void> startImpl(ContextInternal context, Void args) {
        checkpoint.succeed();
        return continuation.future();
      }
      @Override
      protected Future<Void> stopImpl(ContextInternal context, Void args, Duration timeout) {
        stop.incrementAndGet();
        return context.succeededFuture();
      }
    };
    Future<Void> start = resource.start(context, null);
    checkpoint.awaitSuccess();
    vertx.undeploy(context.deploymentID()).await();
    assertEquals(0, stop.get());
    continuation.succeed();
    try {
      start.await();
      fail();
    } catch (IllegalStateException expected) {
    }
    assertEquals(1, stop.get());
    TestUtils.assertWaitUntil(() -> resource.isDone());
  }

  @Test
  public void testStopSuccess() {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    AtomicInteger stop = new AtomicInteger();
    AtomicReference<Duration> timeoutRef = new AtomicReference<>();
    Promise<Void> continuation = context.promise();
    TestResource resource = new TestResource() {
      @Override
      protected Future<Void> stopImpl(ContextInternal context, Void args, Duration timeout) {
        timeoutRef.set(timeout);
        stop.incrementAndGet();
        return continuation.future();
      }
    };
    resource.start(context, null).await();
    Duration timeout = Duration.ofSeconds(1);
    Future<?> stopped = resource.stop((ContextInternal) vertx.getOrCreateContext(), timeout);
    TestUtils.assertWaitUntil(() -> stop.get() == 1);
    continuation.succeed();
    stopped.await();
    TestUtils.assertWaitUntil(() -> resource.isDone());
    assertEquals(timeout, timeoutRef.get());
  }

  @Test
  public void testBareStopSuccess() {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    AtomicInteger stop = new AtomicInteger();
    Promise<Void> continuation = context.promise();
    TestResource resource = new TestResource() {
      @Override
      protected Future<Void> stopImpl(ContextInternal context, Void args, Duration timeout) {
        stop.incrementAndGet();
        return continuation.future();
      }
    };
    resource.stop((ContextInternal) vertx.getOrCreateContext(), Duration.ZERO).await();
    assertEquals(0, stop.get());
    resource.stop((ContextInternal) vertx.getOrCreateContext(), Duration.ZERO).await();
    assertEquals(0, stop.get());
  }

  @Repeat(times = 1000)
  @Test
  public void testStartingStop() {
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    Promise<Void> startContinuation = context.promise();
    Promise<Void> stopContinuation = context.promise();
    AtomicInteger stop = new AtomicInteger();
    TestResource resource = new TestResource() {
      @Override
      public Future<Void> startImpl(ContextInternal context, Void args) {
        return startContinuation.future();
      }
      @Override
      protected Future<Void> stopImpl(ContextInternal context, Void args, Duration timeout) {
        stop.incrementAndGet();
        return stopContinuation.future();
      }
    };
    Future<Void> started = resource.start(context, null);
    Future<?> stopped = resource.stop((ContextInternal) vertx.getOrCreateContext());
    startContinuation.succeed();
    TestUtils.assertWaitUntil(() -> stop.get() == 1);
    assertTrue(started.isComplete());
    assertFalse(stopped.isComplete());
    stopContinuation.succeed();
    started.await();
    stopped.await();
    TestUtils.assertWaitUntil(() -> resource.isDone());
  }

  @Repeat(times = 1000)
  @Test
  public void testStopAfterStartFailure() {
    AtomicInteger stop = new AtomicInteger();
    VertxException failure = new VertxException("");
    Promise<Void> continuation = context.promise();
    TestResource resource = new TestResource() {
      @Override
      protected Future<Void> startImpl(ContextInternal context, Void args) {
        return continuation.future();
      }
      @Override
      protected Future<Void> stopImpl(ContextInternal context, Void args, Duration timeout) {
        stop.incrementAndGet();
        return context.promise();
      }
    };
    Future<Void> started = resource.start((ContextInternal) vertx.getOrCreateContext(), null);
    Future<?> stopped = resource.stop((ContextInternal) vertx.getOrCreateContext());
    continuation.fail(failure);
    try {
      started.await();
      fail();
    } catch (VertxException expected) {
    }
    stopped.await();
    assertEquals(0, stop.get());
    TestUtils.assertWaitUntil(() -> resource.isDone());
  }

  @Repeat(times = 1000)
  @Test
  public void testStartImplFailure() {
    RuntimeException expected = new RuntimeException();
    TestResource resource = new TestResource() {
      @Override
      protected Future<Void> startImpl(ContextInternal context, Void args) {
        throw expected;
      }
    };
    Future<Void> f = resource.start((ContextInternal) vertx.getOrCreateContext(), null);
    TestUtils.assertWaitUntil(f::failed);
  }

  @Repeat(times = 1000)
  @Test
  public void testStopImplFailure() {
    RuntimeException expected = new RuntimeException();
    TestResource resource = new TestResource() {
      @Override
      protected Future<?> stopImpl(ContextInternal context, Void args, Duration timeout) {
        throw expected;
      }
    };
    resource.start((ContextInternal) vertx.getOrCreateContext(), null).await();
    Future<Void> f = resource.stop((ContextInternal) vertx.getOrCreateContext(), null);
    TestUtils.assertWaitUntil(f::succeeded);
  }
}
