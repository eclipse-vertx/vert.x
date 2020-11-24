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

package io.vertx.core;

import io.vertx.test.core.Repeat;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CompositeFutureTest extends VertxTestBase {

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
    testConcurrentCompletion(SUCCESS, CompositeFuture::all, cf -> {
      assertTrue(cf.succeeded());
    });
  }

  @Repeat(times = 100)
  @Test
  public void testConcurrentAllFailure() throws Exception {
    testConcurrentCompletion((x, p) -> p.fail("failure-" + x), CompositeFuture::all, cf -> {
      assertTrue(cf.failed());
    });
  }

  @Repeat(times = 100)
  @Test
  public void testConcurrentAllMixed() throws Exception {
    testConcurrentCompletion(MIXED, CompositeFuture::all, cf -> {
      assertTrue(cf.isComplete());
    });
  }

  @Repeat(times = 100)
  @Test
  public void testConcurrentAnySuccess() throws Exception {
    testConcurrentCompletion(SUCCESS, CompositeFuture::any, cf -> {
      assertTrue(cf.succeeded());
    });
  }

  @Repeat(times = 100)
  @Test
  public void testConcurrentAnyFailure() throws Exception {
    testConcurrentCompletion(FAILURE, CompositeFuture::any, cf -> {
      assertTrue(cf.failed());
    });
  }

  @Repeat(times = 100)
  @Test
  public void testConcurrentAnyMixed() throws Exception {
    testConcurrentCompletion(MIXED, CompositeFuture::any, cf -> {
      assertTrue(cf.isComplete());
    });
  }

  @Repeat(times = 100)
  @Test
  public void tesConcurrenttJoinSuccess() throws Exception {
    testConcurrentCompletion(SUCCESS, CompositeFuture::join, cf -> {
      assertTrue(cf.succeeded());
    });
  }

  @Repeat(times = 100)
  @Test
  public void testConcurrentJoinFailure() throws Exception {
    testConcurrentCompletion((x, p) -> p.fail("failure-" + x), CompositeFuture::join, cf -> {
      assertTrue(cf.failed());
    });
  }

  @Repeat(times = 100)
  @Test
  public void testConcurrentJoinMixed() throws Exception {
    testConcurrentCompletion(MIXED, CompositeFuture::join, cf -> {
      assertTrue(cf.isComplete());
    });
  }

  private void testConcurrentCompletion(BiConsumer<Integer, Promise<String>> completer, Function<List<Future>, CompositeFuture> fact, Consumer<CompositeFuture> check) throws Exception {
    disableThreadChecks();
    List<Promise<String>> promises = IntStream.range(0, NUM_THREADS)
      .mapToObj(i -> Promise.<String>promise())
      .collect(Collectors.toList());
    List<Future> futures = promises.stream()
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
}
