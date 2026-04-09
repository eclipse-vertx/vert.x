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
import io.vertx.core.http.impl.tcp.TcpHttpClientTransport;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.net.NetClientInternal;
import io.vertx.core.net.*;
import io.vertx.core.net.impl.tcp.CleanableNetClient;
import io.vertx.core.internal.net.NetSocketInternal;
import io.vertx.test.core.AsyncTestBase;
import io.vertx.test.core.Repeat;
import io.vertx.test.core.RepeatRule;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.http.HttpTestBase;
import org.junit.Rule;
import io.vertx.test.core.TestUtils;
import org.junit.Assert;
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
import java.util.function.BooleanSupplier;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class VertxTest extends VertxTestBase {

  private static final org.openjdk.jmh.runner.Runner RUNNER = new Runner(new OptionsBuilder().shouldDoGC(true).build());

  public static boolean runGC(BooleanSupplier condition) {
    long now = System.currentTimeMillis();
    while (!condition.getAsBoolean()) {
      if (System.currentTimeMillis() - now >= 20_000) {
        return false;
      }
      runGC();
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        return false;
      }
    }
    return true;
  }

  public static void runGC() {
    RUNNER.runSystemGC();
  }

  @Rule
  public RepeatRule repeatRule = new RepeatRule();

  public VertxTest() {
    super(ReportMode.FORBIDDEN);
  }

  @Test
  public void testCloseHooksCalled() {
    AtomicInteger closedCount = new AtomicInteger();
    Closeable myCloseable1 = completionHandler -> {
      closedCount.incrementAndGet();
      completionHandler.succeed();
    };
    Closeable myCloseable2 = completionHandler -> {
      closedCount.incrementAndGet();
      completionHandler.succeed();
    };
    VertxInternal vertx = (VertxInternal) vertx();
    vertx.addCloseHook(myCloseable1);
    vertx.addCloseHook(myCloseable2);
    // Now undeploy
    vertx.close().await();
    Assert.assertEquals(2, closedCount.get());
  }

  @Test
  public void testCloseHookFailure1() {
    AtomicInteger closedCount = new AtomicInteger();
    class Hook implements Closeable {
      @Override
      public void close(Completable<Void> completion) {
        if (closedCount.incrementAndGet() == 1) {
          throw new RuntimeException("Don't be afraid");
        } else {
          completion.succeed();
        }
      }
    }
    VertxInternal vertx = (VertxInternal) vertx();
    vertx.addCloseHook(new Hook());
    vertx.addCloseHook(new Hook());
    // Now undeploy
    vertx.close().await();
    Assert.assertEquals(2, closedCount.get());
  }

  @Test
  public void testCloseHookFailure2() {
    AtomicInteger closedCount = new AtomicInteger();
    class Hook implements Closeable {
      @Override
      public void close(Completable<Void> completion) {
        if (closedCount.incrementAndGet() == 1) {
          completion.succeed();
          throw new RuntimeException();
        } else {
          completion.succeed();
        }
      }
    }
    VertxInternal vertx = (VertxInternal) vertx();
    vertx.addCloseHook(new Hook());
    vertx.addCloseHook(new Hook());
    // Now undeploy
    vertx
      .close()
      .onComplete(TestUtils.onSuccess(v -> {
        Assert.assertEquals(2, closedCount.get());
        testComplete();
      }));
    await();
  }

  @Test
  public void testCloseFuture() {
    Vertx vertx = vertx();
    Future<Void> fut = vertx.close();
    // Check that we can get a callback on the future as thread pools are closed by the operation
    fut.onComplete(TestUtils.onSuccess(v -> {
      testComplete();
    }));
    await();
  }

  @Test
  public void testFinalizeHttpClient() throws Exception {
    VertxInternal vertx = (VertxInternal) vertx();
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
        .onComplete(TestUtils.onSuccess(server -> latch.countDown()));
      TestUtils.awaitLatch(latch);
      HttpClient client = vertx.createHttpClient();
      // client.connect(1234, "localhost");
      client.request(HttpMethod.GET, HttpTestBase.DEFAULT_HTTP_PORT, "localhost", "/").onSuccess(req -> {
        req.send();
      });
      TcpHttpClientTransport channelConnector = (TcpHttpClientTransport)(((CleanableHttpClient) client).delegate).tcpTransport();
      NetClientInternal netClient = channelConnector.client();
      netClient.closeFuture().onComplete(ar -> {
        closed2.set(true);
      });
      WeakReference<HttpClient> ref = new WeakReference<>(client);
      assertWaitUntil(() -> socketRef.get() != null);
      client = null;
      for (int i = 0;i < 10;i++) {
        Thread.sleep(10);
        runGC();
        Assert.assertFalse(closed1.get());
        Assert.assertNull(ref.get());
      }
      socketRef.get().end(Buffer.buffer(
        "HTTP/1.1 200 OK\r\n" +
          "Content-Length: 0\r\n" +
          "\r\n"
      ));
      long now = System.currentTimeMillis();
      do {
        Assert.assertTrue(System.currentTimeMillis() - now < 20_000);
        runGC();
      } while (!closed1.get());
      now = System.currentTimeMillis();
      do {
        Assert.assertTrue(System.currentTimeMillis() - now < 20_000);
        runGC();
      } while (!closed2.get());
    } finally {
      vertx
        .close()
        .onComplete(TestUtils.onSuccess(v -> testComplete()));
    }
    await();
  }

  @Test
  public void testFinalizeHttpClientWithRequestNotYetSent() throws Exception {
    VertxInternal vertx = (VertxInternal) vertx();
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
        .onComplete(TestUtils.onSuccess(server -> latch.countDown()));
      TestUtils.awaitLatch(latch);
      HttpClient client = vertx.createHttpClient(new PoolOptions().setHttp1MaxSize(1));
      Future<HttpClientRequest> fut = client.request(HttpMethod.GET, HttpTestBase.DEFAULT_HTTP_PORT, "localhost", "/");
      assertWaitUntil(fut::succeeded);
      WeakReference<HttpClient> ref = new WeakReference<>(client);
      client = null;
      runGC();
      Assert.assertNull(ref.get());
      fut.onComplete(TestUtils.onSuccess(req -> {
        req.send().onComplete(TestUtils.onSuccess(resp -> {
          testComplete();
        }));
      }));
      await();
    } finally {
      vertx.close().await();
    }

  }

  @Test
  public void testCascadeCloseHttpClient() throws Exception {
    Vertx vertx1 = vertx();
    try {
      HttpServer server = vertx1.createHttpServer();
      AtomicBoolean connected = new AtomicBoolean();
      server.requestHandler(req -> {
        connected.set(true);
        req.connection().closeHandler(v -> {
          connected.set(false);
        });
      }).listen(HttpTestBase.DEFAULT_HTTP_PORT, "localhost").await();
      VertxInternal vertx2 = (VertxInternal) vertx();
      HttpClient client = vertx2.createHttpClient();
      client.request(HttpMethod.GET, HttpTestBase.DEFAULT_HTTP_PORT, "localhost", "/").onComplete(TestUtils.onSuccess(req -> {
        req.send();
      }));
      waitUntil(connected::get);
      vertx2.close().await();
      waitUntil(() -> !connected.get());
    } finally {
      vertx1.close().await();
    }
  }

  @Test
  public void testFinalizeNetClient() throws Exception {
    VertxInternal vertx = (VertxInternal) vertx();
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
        .onComplete(TestUtils.onSuccess(server -> latch.countDown()));
      TestUtils.awaitLatch(latch);
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
      TestUtils.awaitLatch(latch2);
      WeakReference<NetClient> ref = new WeakReference<>(client);
      assertWaitUntil(() -> socketRef.get() != null);
      client = null;
      for (int i = 0;i < 10;i++) {
        Thread.sleep(10);
        runGC();
        Assert.assertFalse(closed1.get());
        Assert.assertNull(ref.get());
      }
      socketRef.get().close();
      long now = System.currentTimeMillis();
      do {
        Assert.assertTrue(System.currentTimeMillis() - now < 20_000);
        runGC();
      } while (!closed1.get());
      Assert.assertEquals(1, shutdownEventCount.get());
      now = System.currentTimeMillis();
      do {
        Assert.assertTrue(System.currentTimeMillis() - now < 20_000);
        runGC();
      } while (!closed2.get());
    } finally {
      vertx
        .close()
        .onComplete(TestUtils.onSuccess(v -> testComplete()));
    }
    await();
  }

  @Test
  public void testCascadeCloseNetClient() throws Exception {
    Vertx vertx1 = vertx();
    try {
      NetServer server = vertx1.createNetServer();
      AtomicBoolean connected = new AtomicBoolean();
      server.connectHandler(so -> {
        connected.set(true);
        so.closeHandler(v -> {
          connected.set(false);
        });
      }).listen(1234, "localhost").await();
      VertxInternal vertx2 = (VertxInternal) vertx();
      NetClient client = vertx2.createNetClient();
      client.connect(1234, "localhost").onComplete(TestUtils.onSuccess(so -> {
      }));
      waitUntil(connected::get);
      vertx2.close().await();
      waitUntil(() -> !connected.get());
    } finally {
      vertx1.close().await();
    }
  }

  @Test
  public void testCascadeCloseDatagramSocket() throws Exception {
    VertxInternal vertx = (VertxInternal) vertx();
    try {
      DatagramSocket socket = vertx.createDatagramSocket();
      socket.listen(1234, "127.0.0.1").await();
      vertx.close().await();
    } finally {
      vertx
        .close()
        .onComplete(TestUtils.onSuccess(v -> testComplete()));
    }
    await();
  }

  @Test
  public void testFinalizeSharedWorkerExecutor() throws Exception {
    VertxInternal vertx = (VertxInternal) vertx();
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
      Assert.assertFalse(threads[0].isAlive());
      Assert.assertFalse(threads[1].isAlive());
    } finally {
      vertx
        .close()
        .await(20, TimeUnit.SECONDS);
    }
  }

  @Test
  public void testStickContextFinalization() throws Exception {
    Vertx vertx = vertx();
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
        Assert.assertTrue(System.currentTimeMillis() - now < 20_000);
        runGC();
        if (ref.get().get() == null) {
          break;
        }
      }
    } finally {
      vertx
        .close()
        .onComplete(TestUtils.onSuccess(v -> testComplete()));
    }
    await();
  }

  @Test
  public void testCloseVertxShouldWaitConcurrentCloseHook() throws Exception {
    VertxInternal vertx = (VertxInternal) vertx();
    AtomicReference<Completable<Void>> ref = new AtomicReference<>();
    CloseFuture fut = new CloseFuture();
    fut.add(newValue -> ref.set(newValue));
    vertx.addCloseHook(fut);
    Promise<Void> p = Promise.promise();
    fut.close(p);
    AtomicBoolean closed = new AtomicBoolean();
    vertx.close().onComplete(ar -> closed.set(true));
    Thread.sleep(500);
    Assert.assertFalse(closed.get());
    ref.get().succeed();
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
    Vertx vertx = vertx(options);
    ClassLoader orig = Thread.currentThread().getContextClassLoader();
    ClassLoader cl = new URLClassLoader(new URL[0], orig);
    Thread.currentThread().setContextClassLoader(cl);
    Context ctx = vertx.getOrCreateContext();
    Thread.currentThread().setContextClassLoader(orig);
    ctx.runOnContext(v -> {
      ClassLoader expected = disable ? orig : cl;
      Assert.assertSame(expected, Thread.currentThread().getContextClassLoader());
      testComplete();
    });
    await();
  }

  @Repeat(times = 100)
  @Test
  public void testWorkerExecutorConcurrentCloseWithVertx() throws InterruptedException {
    Vertx vertx = vertx();
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
    Vertx vertx = vertx();
    try {
      WorkerExecutor exec = vertx.createSharedWorkerExecutor("pool");
      WeakReference<Thread> ref = exec.executeBlocking(() -> {
        return new WeakReference<>(Thread.currentThread());
      }).await();
      exec.close().await();
      long now = System.currentTimeMillis();
      do {
        Assert.assertTrue(System.currentTimeMillis() - now < 20_000);
        runGC();
      } while (ref.get() != null);
    } finally {
      vertx
        .close()
        .onComplete(TestUtils.onSuccess(v -> testComplete()));
    }
    await();
  }

  @Test
  public void testVersion() {
    Assert.assertNotNull(VertxInternal.version());
  }
}
