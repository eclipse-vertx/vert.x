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

package io.vertx.core;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.impl.CloseFuture;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.NetClientBuilder;
import io.vertx.test.core.AsyncTestBase;
import io.vertx.test.core.Repeat;
import io.vertx.test.core.RepeatRule;
import org.junit.Rule;
import org.junit.Test;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class VertxTest extends AsyncTestBase {

  private static final org.openjdk.jmh.runner.Runner RUNNER = new Runner(new OptionsBuilder().shouldDoGC(true).build());

  public static void runGC() {
    RUNNER.runSystemGC();
  }

  @Rule
  public RepeatRule repeatRule = new RepeatRule();

  @Test
  public void testCloseHooksCalled() {
    AtomicInteger closedCount = new AtomicInteger();
    Closeable myCloseable1 = completionHandler -> {
      closedCount.incrementAndGet();
      completionHandler.handle(Future.succeededFuture());
    };
    Closeable myCloseable2 = completionHandler -> {
      closedCount.incrementAndGet();
      completionHandler.handle(Future.succeededFuture());
    };
    VertxInternal vertx = (VertxInternal) Vertx.vertx();
    vertx.addCloseHook(myCloseable1);
    vertx.addCloseHook(myCloseable2);
    // Now undeploy
    vertx
      .close()
      .onComplete(onSuccess(v -> {
        assertEquals(2, closedCount.get());
        testComplete();
      }));
    await();
  }

  @Test
  public void testCloseHookFailure1() {
    AtomicInteger closedCount = new AtomicInteger();
    class Hook implements Closeable {
      @Override
      public void close(Promise<Void> completion) {
        if (closedCount.incrementAndGet() == 1) {
          throw new RuntimeException("Don't be afraid");
        } else {
          completion.handle(Future.succeededFuture());
        }
      }
    }
    VertxInternal vertx = (VertxInternal) Vertx.vertx();
    vertx.addCloseHook(new Hook());
    vertx.addCloseHook(new Hook());
    // Now undeploy
    vertx
      .close()
      .onComplete(onSuccess(v -> {
        assertEquals(2, closedCount.get());
        testComplete();
      }));
    await();
  }

  @Test
  public void testCloseHookFailure2() throws Exception {
    AtomicInteger closedCount = new AtomicInteger();
    class Hook implements Closeable {
      @Override
      public void close(Promise<Void> completion) {
        if (closedCount.incrementAndGet() == 1) {
          completion.handle(Future.succeededFuture());
          throw new RuntimeException();
        } else {
          completion.handle(Future.succeededFuture());
        }
      }
    }
    VertxInternal vertx = (VertxInternal) Vertx.vertx();
    vertx.addCloseHook(new Hook());
    vertx.addCloseHook(new Hook());
    // Now undeploy
    vertx
      .close()
      .onComplete(onSuccess(v -> {
        assertEquals(2, closedCount.get());
        testComplete();
      }));
    await();
  }

  @Test
  public void testCloseFuture() {
    Vertx vertx = Vertx.vertx();
    Future<Void> fut = vertx.close();
    // Check that we can get a callback on the future as thread pools are closed by the operation
    fut.onComplete(onSuccess(v -> {
      testComplete();
    }));
    await();
  }

  @Test
  public void testFinalizeHttpClient() throws Exception {
    VertxInternal vertx = (VertxInternal) Vertx.vertx();
    try {
      CountDownLatch latch = new CountDownLatch(1);
      AtomicReference<NetSocket> socketRef = new AtomicReference<>();
      vertx.createNetServer()
        .connectHandler(socketRef::set)
        .listen(8080, "localhost")
        .onComplete(onSuccess(server -> latch.countDown()));
      awaitLatch(latch);
      AtomicBoolean closed = new AtomicBoolean();
      // No keep alive so the connection is not held in the pool ????
      CloseFuture closeFuture = new CloseFuture();
      closeFuture.future().onComplete(ar -> closed.set(true));
      HttpClient client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(false), closeFuture);
      vertx.addCloseHook(closeFuture);
      client.request(HttpMethod.GET, 8080, "localhost", "/")
        .compose(HttpClientRequest::send)
        .onComplete(onFailure(err -> {}));
      WeakReference<HttpClient> ref = new WeakReference<>(client);
      closeFuture = null;
      client = null;
      assertWaitUntil(() -> socketRef.get() != null);
      for (int i = 0;i < 10;i++) {
        Thread.sleep(10);
        runGC();
        assertFalse(closed.get());
        assertNotNull(ref.get());
      }
      socketRef.get().close();
      long now = System.currentTimeMillis();
      while (true) {
        assertTrue(System.currentTimeMillis() - now < 20_000);
        runGC();
        if (ref.get() == null) {
          assertTrue(closed.get());
          break;
        }
      }
    } finally {
      vertx
        .close()
        .onComplete(onSuccess(v -> testComplete()));
    }
    await();
  }

  @Test
  public void testFinalizeNetClient() throws Exception {
    VertxInternal vertx = (VertxInternal) Vertx.vertx();
    try {
      CountDownLatch latch = new CountDownLatch(1);
      AtomicReference<NetSocket> socketRef = new AtomicReference<>();
      vertx.createNetServer()
        .connectHandler(socketRef::set)
        .listen(1234, "localhost")
        .onComplete(onSuccess(server -> latch.countDown()));
      awaitLatch(latch);
      AtomicBoolean closed = new AtomicBoolean();
      CloseFuture closeFuture = new CloseFuture();
      NetClient client = new NetClientBuilder(vertx, new NetClientOptions()).closeFuture(closeFuture).build();
      vertx.addCloseHook(closeFuture);
      closeFuture.future().onComplete(ar -> closed.set(true));
      closeFuture = null;
      client.connect(1234, "localhost");
      WeakReference<NetClient> ref = new WeakReference<>(client);
      client = null;
      assertWaitUntil(() -> socketRef.get() != null);
      for (int i = 0;i < 10;i++) {
        Thread.sleep(10);
        runGC();
        assertFalse(closed.get());
        assertNotNull(ref.get());
      }
      socketRef.get().close();
      long now = System.currentTimeMillis();
      while (true) {
        assertTrue(System.currentTimeMillis() - now < 20_000);
        runGC();
        if (ref.get() == null) {
          assertTrue(closed.get());
          break;
        }
      }
    } finally {
      vertx
        .close()
        .onComplete(onSuccess(v -> testComplete()));
    }
    await();
  }

  @Test
  public void testFinalizeSharedWorkerExecutor() throws Exception {
    VertxInternal vertx = (VertxInternal) Vertx.vertx();
    try {
      Thread[] threads = new Thread[2];
      vertx.createSharedWorkerExecutor("LeakTest").executeBlocking(promise -> {
        threads[0] = Thread.currentThread();
        promise.complete();
      }).toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);
      vertx.createSharedWorkerExecutor("LeakTest").executeBlocking(promise -> {
        threads[1] = Thread.currentThread();
        promise.complete();
      }).toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);
      runGC();
      assertFalse(threads[0].isAlive());
      assertFalse(threads[1].isAlive());
    } finally {
      vertx
        .close()
        .toCompletionStage().toCompletableFuture()
        .get(20, TimeUnit.SECONDS);
    }
  }

  @Test
  public void testStickContextFinalization() throws Exception {
    Vertx vertx = Vertx.vertx();
    try {
      AtomicReference<WeakReference<Context>> ref = new AtomicReference<>();
      Thread t = new Thread(() -> {
        Context context = vertx.getOrCreateContext();
        ref.set(new WeakReference<>(context));
        CountDownLatch latch = new CountDownLatch(1);
        context.runOnContext(v -> {
          latch.countDown();
        });
        try {
          latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      });
      t.start();
      t.join(10_000);
      t = null;
      long now = System.currentTimeMillis();
      while (true) {
        assertTrue(System.currentTimeMillis() - now < 20_000);
        runGC();
        if (ref.get().get() == null) {
          break;
        }
      }
    } finally {
      vertx
        .close()
        .onComplete(onSuccess(v -> testComplete()));
    }
    await();
  }

  @Test
  public void testCloseVertxShouldWaitConcurrentCloseHook() throws Exception {
    VertxInternal vertx = (VertxInternal) Vertx.vertx();
    AtomicReference<Promise<Void>> ref = new AtomicReference<>();
    CloseFuture fut = new CloseFuture();
    fut.add(ref::set);
    vertx.addCloseHook(fut);
    Promise<Void> p = Promise.promise();
    fut.close(p);
    AtomicBoolean closed = new AtomicBoolean();
    vertx.close().onComplete(ar -> closed.set(true));
    Thread.sleep(500);
    assertFalse(closed.get());
    ref.get().complete();
    assertWaitUntil(closed::get);
  }

  @Test
  public void testEnableTCCL() {
    testTCCL(false);
  }

  @Test
  public void testDisableTCCL() {
    testTCCL(true);
  }

  private void testTCCL(boolean disable) {
    VertxOptions options = new VertxOptions().setDisableTCCL(disable);
    Vertx vertx = Vertx.vertx(options);
    ClassLoader orig = Thread.currentThread().getContextClassLoader();
    ClassLoader cl = new URLClassLoader(new URL[0], orig);
    Thread.currentThread().setContextClassLoader(cl);
    Context ctx = vertx.getOrCreateContext();
    Thread.currentThread().setContextClassLoader(orig);
    ctx.runOnContext(v -> {
      ClassLoader expected = disable ? orig : cl;
      assertSame(expected, Thread.currentThread().getContextClassLoader());
      testComplete();
    });
    await();
  }

  @Repeat(times = 100)
  @Test
  public void testWorkerExecutorConcurrentCloseWithVertx() throws InterruptedException {
    Vertx vertx = Vertx.vertx();
    try {
      CountDownLatch latch = new CountDownLatch(1);
      WorkerExecutor workerExecutor = vertx.createSharedWorkerExecutor("test");
      vertx.runOnContext(v -> {
        latch.countDown();
        workerExecutor.close();
      });
      latch.await();
    } finally {
      vertx.close();
    }
  }

  @Test
  public void testThreadLeak() throws Exception {
    Vertx vertx = Vertx.vertx();
    try {
      WorkerExecutor exec = vertx.createSharedWorkerExecutor("pool");
      WeakReference<Thread> ref = exec.<WeakReference<Thread>>executeBlocking(p -> {
        p.complete(new WeakReference<>(Thread.currentThread()));
      }).toCompletionStage().toCompletableFuture().get();
      exec.close().toCompletionStage().toCompletableFuture().get();
      long now = System.currentTimeMillis();
      while (true) {
        assertTrue(System.currentTimeMillis() - now < 20_000);
        runGC();
        if (ref.get() == null) {
          break;
        }
      }
    } finally {
      vertx
        .close()
        .onComplete(onSuccess(v -> testComplete()));
    }
    await();
  }
}
