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

package io.vertx.tests.vertx;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.CleanableHttpClient;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.CleanableNetClient;
import io.vertx.core.internal.net.NetSocketInternal;
import io.vertx.test.core.AsyncTestBase;
import io.vertx.test.core.Repeat;
import io.vertx.test.core.RepeatRule;
import io.vertx.test.http.HttpTestBase;
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
      AtomicBoolean closed1 = new AtomicBoolean();
      AtomicBoolean closed2 = new AtomicBoolean();
      CountDownLatch latch = new CountDownLatch(1);
      AtomicReference<NetSocket> socketRef = new AtomicReference<>();
      vertx.createNetServer()
        .connectHandler(so -> {
          socketRef.set(so);
          so.closeHandler(v -> {
            closed1.set(true);
          });
        })
        .listen(HttpTestBase.DEFAULT_HTTP_PORT, "localhost")
        .onComplete(onSuccess(server -> latch.countDown()));
      awaitLatch(latch);
      HttpClient client = vertx.createHttpClient();
      // client.connect(1234, "localhost");
      client.request(HttpMethod.GET, HttpTestBase.DEFAULT_HTTP_PORT, "localhost", "/").onSuccess(req -> {
        req.send();
      });
      (((CleanableHttpClient)client).delegate).netClient().closeFuture().onComplete(ar -> {
        closed2.set(true);
      });
      WeakReference<HttpClient> ref = new WeakReference<>(client);
      assertWaitUntil(() -> socketRef.get() != null);
      client = null;
      for (int i = 0;i < 10;i++) {
        Thread.sleep(10);
        runGC();
        assertFalse(closed1.get());
        assertNull(ref.get());
      }
      socketRef.get().end(Buffer.buffer(
        "HTTP/1.1 200 OK\r\n" +
          "Content-Length: 0\r\n" +
          "\r\n"
      ));
      long now = System.currentTimeMillis();
      do {
        assertTrue(System.currentTimeMillis() - now < 20_000);
        runGC();
      } while (!closed1.get());
      now = System.currentTimeMillis();
      do {
        assertTrue(System.currentTimeMillis() - now < 20_000);
        runGC();
      } while (!closed2.get());
    } finally {
      vertx
        .close()
        .onComplete(onSuccess(v -> testComplete()));
    }
    await();
  }

  @Test
  public void testFinalizeHttpClientWithRequestNotYetSent() throws Exception {
    VertxInternal vertx = (VertxInternal) Vertx.vertx();
    try {
      CountDownLatch latch = new CountDownLatch(1);
      vertx.createNetServer()
        .connectHandler(so -> {
          so.handler(buff -> {
            so.write("HTTP/1.1 200 OK\r\n" +
              "Content-Length: 0\r\n" +
              "\r\n");
          });
        })
        .listen(HttpTestBase.DEFAULT_HTTP_PORT, "localhost")
        .onComplete(onSuccess(server -> latch.countDown()));
      awaitLatch(latch);
      HttpClient client = vertx.createHttpClient(new PoolOptions().setHttp1MaxSize(1));
      Future<HttpClientRequest> fut = client.request(HttpMethod.GET, HttpTestBase.DEFAULT_HTTP_PORT, "localhost", "/");
      assertWaitUntil(fut::succeeded);
      WeakReference<HttpClient> ref = new WeakReference<>(client);
      client = null;
      runGC();
      assertNull(ref.get());
      fut.onComplete(onSuccess(req -> {
        req.send().onComplete(onSuccess(resp -> {
          testComplete();
        }));
      }));
      await();
    } finally {
      awaitFuture(vertx.close());
    }

  }

  @Test
  public void testCascadeCloseHttpClient() throws Exception {
    Vertx vertx1 = Vertx.vertx();
    try {
      HttpServer server = vertx1.createHttpServer();
      AtomicBoolean connected = new AtomicBoolean();
      awaitFuture(server.requestHandler(req -> {
        connected.set(true);
        req.connection().closeHandler(v -> {
          connected.set(false);
        });
      }).listen(HttpTestBase.DEFAULT_HTTP_PORT, "localhost"));
      VertxInternal vertx2 = (VertxInternal) Vertx.vertx();
      HttpClient client = vertx2.createHttpClient();
      client.request(HttpMethod.GET, HttpTestBase.DEFAULT_HTTP_PORT, "localhost", "/").onComplete(onSuccess(req -> {
        req.send();
      }));
      waitUntil(connected::get);
      awaitFuture(vertx2.close());
      waitUntil(() -> !connected.get());
    } finally {
      awaitFuture(vertx1.close());
    }
  }

  @Test
  public void testFinalizeNetClient() throws Exception {
    VertxInternal vertx = (VertxInternal) Vertx.vertx();
    try {
      AtomicBoolean closed1 = new AtomicBoolean();
      AtomicBoolean closed2 = new AtomicBoolean();
      CountDownLatch latch = new CountDownLatch(1);
      AtomicReference<NetSocket> socketRef = new AtomicReference<>();
      vertx.createNetServer()
        .connectHandler(so -> {
          socketRef.set(so);
          so.closeHandler(v -> {
            closed1.set(true);
          });
        })
        .listen(1234, "localhost")
        .onComplete(onSuccess(server -> latch.countDown()));
      awaitLatch(latch);
      NetClient client = vertx.createNetClient();
      CountDownLatch latch2 = new CountDownLatch(1);
      AtomicInteger shutdownEventCount = new AtomicInteger();
      client.connect(1234, "localhost").onComplete(ar -> {
        if (ar.succeeded()) {
          NetSocketInternal so = (NetSocketInternal) ar.result();
          so.eventHandler(v -> shutdownEventCount.incrementAndGet());
          latch2.countDown();
        }
      });
      ((CleanableNetClient)client).unwrap().closeFuture().onComplete(ar -> {
        closed2.set(true);
      });
      awaitLatch(latch2);
      WeakReference<NetClient> ref = new WeakReference<>(client);
      assertWaitUntil(() -> socketRef.get() != null);
      client = null;
      for (int i = 0;i < 10;i++) {
        Thread.sleep(10);
        runGC();
        assertFalse(closed1.get());
        assertNull(ref.get());
      }
      socketRef.get().close();
      long now = System.currentTimeMillis();
      do {
        assertTrue(System.currentTimeMillis() - now < 20_000);
        runGC();
      } while (!closed1.get());
      assertEquals(1, shutdownEventCount.get());
      now = System.currentTimeMillis();
      do {
        assertTrue(System.currentTimeMillis() - now < 20_000);
        runGC();
      } while (!closed2.get());
    } finally {
      vertx
        .close()
        .onComplete(onSuccess(v -> testComplete()));
    }
    await();
  }

  @Test
  public void testCascadeCloseNetClient() throws Exception {
    Vertx vertx1 = Vertx.vertx();
    try {
      NetServer server = vertx1.createNetServer();
      AtomicBoolean connected = new AtomicBoolean();
      awaitFuture(server.connectHandler(so -> {
        connected.set(true);
        so.closeHandler(v -> {
          connected.set(false);
        });
      }).listen(1234, "localhost"));
      VertxInternal vertx2 = (VertxInternal) Vertx.vertx();
      NetClient client = vertx2.createNetClient();
      client.connect(1234, "localhost").onComplete(onSuccess(so -> {
      }));
      waitUntil(connected::get);
      awaitFuture(vertx2.close());
      waitUntil(() -> !connected.get());
    } finally {
      awaitFuture(vertx1.close());
    }
  }

  @Test
  public void testCascadeCloseDatagramSocket() throws Exception {
    VertxInternal vertx = (VertxInternal) Vertx.vertx();
    try {
      DatagramSocket socket = vertx.createDatagramSocket();
      awaitFuture(socket.listen(1234, "127.0.0.1"));
      awaitFuture(vertx.close());
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
      vertx.createSharedWorkerExecutor("LeakTest").executeBlocking(() -> {
        threads[0] = Thread.currentThread();
        return null;
      }).await(20, TimeUnit.SECONDS);
      vertx.createSharedWorkerExecutor("LeakTest").executeBlocking(() -> {
        threads[1] = Thread.currentThread();
        return null;
      }).await(20, TimeUnit.SECONDS);
      runGC();
      assertFalse(threads[0].isAlive());
      assertFalse(threads[1].isAlive());
    } finally {
      vertx
        .close()
        .await(20, TimeUnit.SECONDS);
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
      WeakReference<Thread> ref = exec.executeBlocking(() -> {
        return new WeakReference<>(Thread.currentThread());
      }).await();
      exec.close().await();
      long now = System.currentTimeMillis();
      do {
        assertTrue(System.currentTimeMillis() - now < 20_000);
        runGC();
      } while (ref.get() != null);
    } finally {
      vertx
        .close()
        .onComplete(onSuccess(v -> testComplete()));
    }
    await();
  }

  @Test
  public void testVersion() {
    assertNotNull(VertxInternal.version());
  }
}
