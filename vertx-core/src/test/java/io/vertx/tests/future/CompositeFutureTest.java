/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tests.future;

import io.vertx.core.*;
import io.vertx.core.impl.future.FutureImpl;
import io.vertx.test.core.Repeat;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CompositeFutureTest extends FutureTestBase {

  private static final int NUM_THREADS = 4;
  private static final BiConsumer<Integer, Promise<String>> MIXED = (x, p) -> {
    if (x % 2 == 0) {
      p.complete("success-" + x);
    } else {
      p.complete("failure-" + x);
    }
  };

  private static final BiConsumer<Integer, Promise<String>> SUCCESS = (x, p) -> p.complete("success-" + x);
  private static final BiConsumer<Integer, Promise<String>> FAILURE = (x, p) -> p.fail("failure-" + x);

  @Repeat(times = 100)
  @Test
  public void testConcurrentAllSuccess() throws Exception {
    testConcurrentCompletion(SUCCESS, Future::all, cf -> {
      assertTrue(cf.succeeded());
    });
  }

  @Repeat(times = 100)
  @Test
  public void testConcurrentAllFailure() throws Exception {
    testConcurrentCompletion((x, p) -> p.fail("failure-" + x), Future::all, cf -> {
      assertTrue(cf.failed());
    });
  }

  @Repeat(times = 100)
  @Test
  public void testConcurrentAllMixed() throws Exception {
    testConcurrentCompletion(MIXED, Future::all, cf -> {
      assertTrue(cf.isComplete());
    });
  }

  @Repeat(times = 100)
  @Test
  public void testConcurrentAnySuccess() throws Exception {
    testConcurrentCompletion(SUCCESS, Future::any, cf -> {
      assertTrue(cf.succeeded());
    });
  }

  @Repeat(times = 100)
  @Test
  public void testConcurrentAnyFailure() throws Exception {
    testConcurrentCompletion(FAILURE, Future::any, cf -> {
      assertTrue(cf.failed());
    });
  }

  @Repeat(times = 100)
  @Test
  public void testConcurrentAnyMixed() throws Exception {
    testConcurrentCompletion(MIXED, Future::any, cf -> {
      assertTrue(cf.isComplete());
    });
  }

  @Repeat(times = 100)
  @Test
  public void tesConcurrenttJoinSuccess() throws Exception {
    testConcurrentCompletion(SUCCESS, Future::join, cf -> {
      assertTrue(cf.succeeded());
    });
  }

  @Repeat(times = 100)
  @Test
  public void testConcurrentJoinFailure() throws Exception {
    testConcurrentCompletion((x, p) -> p.fail("failure-" + x), Future::join, cf -> {
      assertTrue(cf.failed());
    });
  }

  @Repeat(times = 100)
  @Test
  public void testConcurrentJoinMixed() throws Exception {
    testConcurrentCompletion(MIXED, Future::join, cf -> {
      assertTrue(cf.isComplete());
    });
  }

  private void testConcurrentCompletion(BiConsumer<Integer, Promise<String>> completer, Function<List<Future<?>>, CompositeFuture> fact, Consumer<CompositeFuture> check) throws Exception {
    disableThreadChecks();
    List<Promise<String>> promises = IntStream.range(0, NUM_THREADS)
      .mapToObj(i -> Promise.<String>promise())
      .collect(Collectors.toList());
    List<Future<?>> futures = promises.stream()
      .map(Promise::future)
      .collect(Collectors.toList());
    CompositeFuture compositeFuture = fact.apply(futures);
    ExecutorService executorService = Executors.newFixedThreadPool(NUM_THREADS);
    CyclicBarrier barrier = new CyclicBarrier(NUM_THREADS);
    for (int i = 0; i < NUM_THREADS; ++i) {
      final int x = i;
      executorService.submit(() -> {
        Promise<String> promise = promises.get(x);
        try {
          barrier.await();
          completer.accept(x, promise);
        } catch (Throwable t) {
          fail(t);
        }
      });
    }
    compositeFuture.onComplete(x -> {
      check.accept(compositeFuture);
      testComplete();
    });
    executorService.shutdown();
    executorService.awaitTermination(30, TimeUnit.SECONDS);
    await();
  }

  @Test
  public void testAllSucceeded() {
    testAllSucceeded(Future::all);
  }

  @Test
  public void testAllSucceededWithList() {
    testAllSucceeded((f1, f2) -> Future.all(Arrays.asList(f1, f2)));
  }

  private void testAllSucceeded(BiFunction<Future<String>, Future<Integer>, CompositeFuture> all) {
    Promise<String> p1 = Promise.promise();
    Future<String> f1 = p1.future();
    Promise<Integer> p2 = Promise.promise();
    Future<Integer> f2 = p2.future();
    CompositeFuture composite = all.apply(f1, f2);
    Checker<CompositeFuture> checker = new Checker<>(composite);
    checker.assertNotCompleted();
    assertEquals(null, composite.<String>resultAt(0));
    assertEquals(null, composite.<Integer>resultAt(1));
    p1.complete("something");
    checker.assertNotCompleted();
    assertEquals("something", composite.resultAt(0));
    assertEquals(null, composite.<Integer>resultAt(1));
    p2.complete(3);
    checker.assertSucceeded(composite);
    assertEquals("something", composite.resultAt(0));
    assertEquals(3, (int)composite.resultAt(1));
  }

  @Test
  public void testAllWithEmptyList() {
    Future composite = Future.all(Collections.emptyList());
    assertTrue(composite.isComplete());
  }

  @Test
  public void testAllFailed() {
    testAllFailed(Future::all);
  }

  @Test
  public void testAllFailedWithList() {
    testAllFailed((f1, f2) -> Future.all(Arrays.asList(f1, f2)));
  }

  private void testAllFailed(BiFunction<Future<String>, Future<Integer>, CompositeFuture> all) {
    Promise<String> p1 = Promise.promise();
    Future<String> f1 = p1.future();
    Promise<Integer> p2 = Promise.promise();
    Future<Integer> f2 = p2.future();
    CompositeFuture composite = all.apply(f1, f2);
    Checker<CompositeFuture> checker = new Checker<>(composite);
    p1.complete("s");
    Exception cause = new Exception();
    p2.fail(cause);
    checker.assertFailed(cause);
    assertEquals("s", composite.resultAt(0));
    assertEquals(null, composite.<Integer>resultAt(1));
  }

  @Test
  public void testAllLargeList() {
    testAllLargeList(63);
    testAllLargeList(64);
    testAllLargeList(65);
    testAllLargeList(100);
  }

  private void testAllLargeList(int size) {
    List<Future<?>> list = new ArrayList<>();
    for (int i = 0;i < size;i++) {
      list.add(Future.succeededFuture());
    }
    CompositeFuture composite = Future.all(list);
    Checker<CompositeFuture> checker = new Checker<>(composite);
    checker.assertSucceeded(composite);
    for (int i = 0;i < size;i++) {
      list.clear();
      Throwable cause = new Exception();
      for (int j = 0;j < size;j++) {
        list.add(i == j ? Future.failedFuture(cause) : Future.succeededFuture());
      }
      composite = Future.all(list);
      checker = new Checker<>(composite);
      checker.assertFailed(cause);
      for (int j = 0;j < size;j++) {
        if (i == j) {
          assertTrue(composite.failed(j));
        } else {
          assertTrue(composite.succeeded(j));
        }
      }
    }
  }

  @Test
  public void testAnySucceeded1() {
    testAnySucceeded1(Future::any);
  }

  @Test
  public void testAnySucceeded1WithList() {
    testAnySucceeded1((f1, f2) -> Future.any(Arrays.asList(f1, f2)));
  }

  private void testAnySucceeded1(BiFunction<Future<String>, Future<Integer>, CompositeFuture> any) {
    Promise<String> p1 = Promise.promise();
    Future<String> f1 = p1.future();
    Promise<Integer> p2 = Promise.promise();
    Future<Integer> f2 = p2.future();
    CompositeFuture composite = any.apply(f1, f2);
    Checker<CompositeFuture> checker = new Checker<>(composite);
    checker.assertNotCompleted();
    assertEquals(null, composite.<String>resultAt(0));
    assertEquals(null, composite.<Integer>resultAt(1));
    p1.complete("something");
    checker.assertSucceeded(composite);
    p2.complete(3);
    checker.assertSucceeded(composite);
  }

  @Test
  public void testAnyWithEmptyList() {
    CompositeFuture composite = Future.any(Collections.emptyList());
    assertTrue(composite.isComplete());
  }

  @Test
  public void testAnySucceeded2() {
    testAnySucceeded2(Future::any);
  }

  @Test
  public void testAnySucceeded2WithList() {
    testAnySucceeded2(Future::any);
  }

  private void testAnySucceeded2(BiFunction<Future<String>, Future<Integer>, CompositeFuture> any) {
    Promise<String> p1 = Promise.promise();
    Future<String> f1 = p1.future();
    Promise<Integer> p2 = Promise.promise();
    Future<Integer> f2 = p2.future();
    CompositeFuture composite = any.apply(f1, f2);
    Checker<CompositeFuture> checker = new Checker<>(composite);
    p1.fail("failure");
    checker.assertNotCompleted();
    p2.complete(3);
    checker.assertSucceeded(composite);
  }

  @Test
  public void testAnyFailed() {
    testAnyFailed(Future::any);
  }

  @Test
  public void testAnyFailedWithList() {
    testAnyFailed((f1, f2) -> Future.any(Arrays.asList(f1, f2)));
  }

  private void testAnyFailed(BiFunction<Future<String>, Future<Integer>, CompositeFuture> any) {
    Promise<String> p1 = Promise.promise();
    Future<String> f1 = p1.future();
    Promise<Integer> p2 = Promise.promise();
    Future<Integer> f2 = p2.future();
    CompositeFuture composite = any.apply(f1, f2);
    Checker<CompositeFuture> checker = new Checker<>(composite);
    p1.fail("failure");
    checker.assertNotCompleted();
    Throwable cause = new Exception();
    p2.fail(cause);
    checker.assertFailed(cause);
  }

  @Test
  public void testAnyLargeList() {
    testAnyLargeList(63);
    testAnyLargeList(64);
    testAnyLargeList(65);
    testAnyLargeList(100);
  }

  private void testAnyLargeList(int size) {
    List<Future<?>> list = new ArrayList<>();
    for (int i = 0;i < size;i++) {
      list.add(Future.failedFuture(new Exception()));
    }
    CompositeFuture composite = Future.any(list);
    Checker<CompositeFuture> checker = new Checker<>(composite);
    assertNotNull(checker.assertFailed());
    for (int i = 0;i < size;i++) {
      list.clear();
      for (int j = 0;j < size;j++) {
        list.add(i == j ? Future.succeededFuture() : Future.failedFuture(new RuntimeException()));
      }
      composite = Future.any(list);
      checker = new Checker<>(composite);
      checker.assertSucceeded(composite);
      for (int j = 0;j < size;j++) {
        if (i == j) {
          assertTrue(composite.succeeded(j));
        } else {
          assertTrue(composite.failed(j));
        }
      }
    }
  }

  @Test
  public void testJoinSucceeded() {
    testJoinSucceeded(Future::join);
  }

  @Test
  public void testJoinSucceededWithList() {
    testJoinSucceeded((f1, f2) -> Future.join(Arrays.asList(f1, f2)));
  }

  private void testJoinSucceeded(BiFunction<Future<String>, Future<Integer>, CompositeFuture> join) {
    Promise<String> p1 = Promise.promise();
    Future<String> f1 = p1.future();
    Promise<Integer> p2 = Promise.promise();
    Future<Integer> f2 = p2.future();
    CompositeFuture composite = join.apply(f1, f2);
    Checker<CompositeFuture> checker = new Checker<>(composite);
    checker.assertNotCompleted();
    p1.complete("foo");
    checker.assertNotCompleted();
    p2.complete();
    checker.assertSucceeded(composite);
  }

  @Test
  public void testJoinFailed1() {
    testJoinFailed1(Future::join);
  }

  @Test
  public void testJoinFailed1WithList() {
    testJoinFailed1((f1, f2) -> Future.join(Arrays.asList(f1, f2)));
  }

  private void testJoinFailed1(BiFunction<Future<String>, Future<Integer>, CompositeFuture> join) {
    Promise<String> p1 = Promise.promise();
    Future<String> f1 = p1.future();
    Promise<Integer> p2 = Promise.promise();
    Future<Integer> f2 = p2.future();
    CompositeFuture composite = join.apply(f1, f2);
    Checker<CompositeFuture> checker = new Checker<>(composite);
    checker.assertNotCompleted();
    p1.complete("foo");
    checker.assertNotCompleted();
    Throwable cause = new Throwable();
    p2.fail(cause);
    assertSame(checker.assertFailed(), cause);
  }

  @Test
  public void testJoinFailed2() {
    testJoinFailed2(Future::join);
  }

  @Test
  public void testJoinFailed2WithList() {
    testJoinFailed2((f1, f2) -> Future.join(Arrays.asList(f1, f2)));
  }

  private void testJoinFailed2(BiFunction<Future<String>, Future<Integer>, CompositeFuture> join) {
    Promise<String> p1 = Promise.promise();
    Future<String> f1 = p1.future();
    Promise<Integer> p2 = Promise.promise();
    Future<Integer> f2 = p2.future();
    CompositeFuture composite = join.apply(f1, f2);
    Checker<CompositeFuture> checker = new Checker<>(composite);
    checker.assertNotCompleted();
    Throwable cause = new Throwable();
    p1.fail(cause);
    checker.assertNotCompleted();
    p2.complete(10);
    assertSame(cause, checker.assertFailed());
  }

  @Test
  public void testJoinFailed3() {
    testJoinFailed3(Future::join);
  }

  @Test
  public void testJoinFailed3WithList() {
    testJoinFailed3((f1, f2) -> Future.join(Arrays.asList(f1, f2)));
  }

  private void testJoinFailed3(BiFunction<Future<String>, Future<Integer>, CompositeFuture> join) {
    Promise<String> p1 = Promise.promise();
    Future<String> f1 = p1.future();
    Promise<Integer> p2 = Promise.promise();
    Future<Integer> f2 = p2.future();
    CompositeFuture composite = join.apply(f1, f2);
    Checker<CompositeFuture> checker = new Checker<>(composite);
    checker.assertNotCompleted();
    Throwable cause1 = new Throwable();
    p1.fail(cause1);
    checker.assertNotCompleted();
    Throwable cause2 = new Throwable();
    p2.fail(cause2);
    assertSame(cause1, checker.assertFailed());
  }

  @Test
  public void testJoinWithEmptyList() {
    CompositeFuture composite = Future.join(Collections.emptyList());
    assertTrue(composite.isComplete());
  }

  @Test
  public void testCompositeFutureToList() {
    Promise<String> p1 = Promise.promise();
    Future<String> f1 = p1.future();
    Promise<Integer> p2 = Promise.promise();
    Future<Integer> f2 = p2.future();
    CompositeFuture composite = Future.all(f1, f2);
    assertEquals(Arrays.asList(null, null), composite.list());
    p1.complete("foo");
    assertEquals(Arrays.asList("foo", null), composite.list());
    p2.complete(4);
    assertEquals(Arrays.asList("foo", 4), composite.list());
  }

  @Test
  public void testCompositeFutureCauses() {
    CompositeFuture composite = Future.all(Future.failedFuture("blabla"), Future.succeededFuture());

    assertEquals(2, composite.causes().size());
    assertNotNull(composite.causes().get(0));
    assertEquals("blabla", composite.causes().get(0).getMessage());

    assertNull(composite.causes().get(1));
  }

  @Test
  public void testCompositeFutureMulti() {
    Promise<String> p1 = Promise.promise();
    Future<String> f1 = p1.future();
    Promise<Integer> p2 = Promise.promise();
    Future<Integer> f2 = p2.future();
    CompositeFuture composite = Future.all(f1, f2);
    AtomicInteger count = new AtomicInteger();
    composite.onComplete(ar -> {
      count.compareAndSet(0, 1);
    });
    composite.onComplete(ar -> {
      count.compareAndSet(1, 2);
    });
    p1.complete("foo");
    p2.complete(4);
    assertEquals(2, count.get());
  }

  private void testIndexOutOfBounds(ThrowingCallable throwingCallable) {
    assertThatThrownBy(throwingCallable)
    .isExactlyInstanceOf(IndexOutOfBoundsException.class).hasMessage(null);
  }

  @Test
  public void testIndexOutOfBounds() {
    CompositeFuture composite = Future.all(Future.succeededFuture(), Future.succeededFuture());
    testIndexOutOfBounds(() -> composite.resultAt(-2));
    testIndexOutOfBounds(() -> composite.resultAt(-1));
    testIndexOutOfBounds(() -> composite.resultAt(2));
    testIndexOutOfBounds(() -> composite.resultAt(3));
  }

  @Test
  public void testToString() {
    assertEquals("Future{result=(Future{result=null},Future{result=null})}", Future.all(Future.succeededFuture(), Future.succeededFuture()).toString());
    assertEquals("Future{result=(Future{result=true},Future{result=false})}", Future.all(Future.succeededFuture(true), Future.succeededFuture(false)).toString());
  }

  private static final class MonitoringFuture extends FutureImpl<Void> {
    Set<Completable<Void>> listeners = new HashSet<>();
    @Override
    public void addListener(Completable<Void> listener) {
      listeners.add(listener);
      super.addListener(listener);
    }
    @Override
    public void removeListener(Completable<Void> listener) {
      listeners.remove(listener);
      super.removeListener(listener);
    }
  }

  @Test
  public void testAllRemovesListeners1() {
    MonitoringFuture f = new MonitoringFuture();
    Future.all(Future.failedFuture(""), f);
    assertEquals(Collections.emptySet(), f.listeners);
  }

  @Test
  public void testAllRemovesListeners2() {
    MonitoringFuture f = new MonitoringFuture();
    Future.all(f, Future.failedFuture(""));
    assertEquals(Collections.emptySet(), f.listeners);
  }

  @Test
  public void testAnyRemovesListeners1() {
    MonitoringFuture f = new MonitoringFuture();
    Future.any(Future.succeededFuture(), f);
    assertEquals(Collections.emptySet(), f.listeners);
  }

  @Test
  public void testAnyRemovesListeners2() {
    MonitoringFuture f = new MonitoringFuture();
    Future.any(f, Future.succeededFuture());
    assertEquals(Collections.emptySet(), f.listeners);
  }

  @Test
  public void testCustomFuture() {
    Promise<Void> p1 = Promise.promise();
    Promise<Void> p2 = Promise.promise();
    Promise<Void> p3 = Promise.promise();

    CompositeFuture cf = Future.all(p1.future(), new MyFuture(p2), p3.future());

    p1.complete(null);
    p2.complete(null);
    p3.complete(null);

    assertTrue(cf.isComplete());
  }

  private static class MyFuture implements Future<Void> {

    private final Future<Void> delegate;

    private MyFuture(Promise<Void> promise) {
      delegate = promise.future();
    }

    @Override
    public boolean isComplete() {
      return delegate.isComplete();
    }

    @Override
    public Future<Void> onComplete(Handler<AsyncResult<Void>> handler) {
      return delegate.onComplete(handler);
    }

    @Override
    public Void result() {
      return delegate.result();
    }

    @Override
    public Throwable cause() {
      return delegate.cause();
    }

    @Override
    public boolean succeeded() {
      return delegate.succeeded();
    }

    @Override
    public boolean failed() {
      return delegate.failed();
    }

    @Override
    public <U> Future<U> compose(Function<? super Void, Future<U>> successMapper, Function<Throwable, Future<U>> failureMapper) {
      return delegate.compose(successMapper, failureMapper);
    }

    @Override
    public <U> Future<U> transform(Function<AsyncResult<Void>, Future<U>> mapper) {
      return delegate.transform(mapper);
    }

    @Override
    public <U> Future<Void> eventually(Supplier<Future<U>> mapper) {
      return delegate.eventually(mapper);
    }

    @Override
    public <U> Future<U> map(Function<? super Void, U> mapper) {
      return delegate.map(mapper);
    }

    @Override
    public <V> Future<V> map(V value) {
      return delegate.map(value);
    }

    @Override
    public Future<Void> otherwise(Function<Throwable, Void> mapper) {
      return delegate.otherwise(mapper);
    }

    @Override
    public Future<Void> otherwise(Void value) {
      return delegate.otherwise(value);
    }

    @Override
    public Future<Void> expecting(Expectation<? super Void> expectation) {
      return delegate.expecting(expectation);
    }

    @Override
    public Future<Void> timeout(long delay, TimeUnit unit) {
      return delegate.timeout(delay, unit);
    }
  }
}
