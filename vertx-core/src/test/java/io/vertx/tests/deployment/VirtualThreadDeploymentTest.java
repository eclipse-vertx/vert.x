/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.deployment;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.http.HttpTestBase;
import junit.framework.AssertionFailedError;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class VirtualThreadDeploymentTest extends VertxTestBase {

  static {
    Method isVirtualMethod = null;
    try {
      isVirtualMethod = Thread.class.getDeclaredMethod("isVirtual");
    } catch (NoSuchMethodException ignore) {
    }
    IS_VIRTUAL = isVirtualMethod;
  }

  private static final Method IS_VIRTUAL;

  public static boolean isVirtual(Thread th) {
    if (IS_VIRTUAL != null) {
      try {
        return (boolean) IS_VIRTUAL.invoke(th);
      } catch (Exception e) {
        AssertionFailedError afe = new AssertionFailedError();
        afe.initCause(e);
        throw afe;
      }
    } else {
      return false;
    }
  }

  @Test
  public void testDeploy() {
    Assume.assumeTrue(isVirtualThreadAvailable());
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() {
        assertTrue(isVirtual(Thread.currentThread()));
        Future<Void> fut = Future.future(p -> vertx.setTimer(500, id -> p.complete()));
        fut.await();
        testComplete();
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD));
    await();
  }

  @Test
  public void testExecuteBlocking() {
    Assume.assumeTrue(isVirtualThreadAvailable());
    Promise<Void> p = Promise.promise();
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() {
        Future<String> fut = vertx.executeBlocking(() -> {
          assertTrue(isVirtual(Thread.currentThread()));
          return Thread.currentThread().getName();
        });
        String res;
        try {
          res = fut.await();
        } catch (Exception e) {
          p.fail(e);
          return;
        }
        assertNotSame(Thread.currentThread().getName(), res);
        p.complete();
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD)).await();
  }

  @Test
  public void testUndeployInterruptVirtualThreads() throws Exception {
    int num = 16;
    Promise<Void> p = Promise.promise();
    AtomicReference<Thread> thread = new AtomicReference<>();
    AtomicInteger interrupted = new AtomicInteger();
    CountDownLatch latch = new CountDownLatch(num);
    Assume.assumeTrue(isVirtualThreadAvailable());
    String id = vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() {
        for (int i = 0;i < num;i++) {
          vertx.runOnContext(v -> {
            try {
              thread.set(Thread.currentThread());
              latch.countDown();
              p.future().await();
            } catch (Exception e) {
              if (e instanceof InterruptedException) {
                interrupted.incrementAndGet();
              }
            }
          });
        }
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD))
      .await(20, TimeUnit.SECONDS);
    latch.await(20, TimeUnit.SECONDS);
    assertWaitUntil(() -> thread.get() != null && thread.get().getState() == Thread.State.WAITING);
    vertx.undeploy(id).await(20, TimeUnit.SECONDS);
    assertWaitUntil(() -> interrupted.get() == num);
  }

  @Test
  public void testDeployHTTPServer() throws Exception {
    Assume.assumeTrue(isVirtualThreadAvailable());
    AtomicInteger inflight = new AtomicInteger();
    AtomicBoolean processing = new AtomicBoolean();
    AtomicInteger max = new AtomicInteger();
    vertx.deployVerticle(new AbstractVerticle() {
        HttpServer server;
        @Override
        public void start() {
          server = vertx.createHttpServer().requestHandler(req -> {
            assertFalse(processing.getAndSet(true));
            int val = inflight.incrementAndGet();
            max.set(Math.max(val, max.get()));
            Future<Void> fut = Future.future(p -> vertx.setTimer(50, id -> p.complete()));
            processing.set(false);
            fut.await();
            assertFalse(processing.getAndSet(true));
            req.response().end();
            inflight.decrementAndGet();
            processing.set(false);
          });
          server.listen(HttpTestBase.DEFAULT_HTTP_PORT, "localhost").await();
        }
      }, new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD))
      .await();
    HttpClient client = vertx.createHttpClient();
    int numReq = 10;
    waitFor(numReq);
    for (int i = 0;i < numReq;i++) {
      Future<Buffer> resp = client.request(HttpMethod.GET, HttpTestBase.DEFAULT_HTTP_PORT, "localhost", "/")
        .compose(req -> req.send()
          .compose(HttpClientResponse::body));
      resp.onComplete(onSuccess(v -> complete()));
    }
    await();
    Assert.assertEquals(5, max.get());
  }
  @Test
  public void testHttpClientStopRequestInProgress() throws Exception {
    Assume.assumeTrue(isVirtualThreadAvailable());
    AtomicInteger inflight = new AtomicInteger();
    vertx.createHttpServer().requestHandler(request -> {
      inflight.incrementAndGet();
    }).listen(HttpTestBase.DEFAULT_HTTP_PORT, HttpTestBase.DEFAULT_HTTP_HOST);
    int numReq = 10;
    Set<Thread> threads = Collections.synchronizedSet(new HashSet<>());
    Set<Thread> interruptedThreads = Collections.synchronizedSet(new HashSet<>());
    String deploymentID = vertx.deployVerticle(new VerticleBase() {
        HttpClient client;
        @Override
        public Future<?> start() throws Exception {
          client = vertx.createHttpClient(new PoolOptions().setHttp1MaxSize(numReq));
          for (int i = 0;i < numReq;i++) {
            vertx.runOnContext(v -> {
              threads.add(Thread.currentThread());
              try {
                client
                  .request(HttpMethod.GET, HttpTestBase.DEFAULT_HTTP_PORT, "localhost", "/")
                  .compose(HttpClientRequest::send)
                  .await();
              } catch (Throwable e) {
                interruptedThreads.add(Thread.currentThread());
              }
            });
          }
          return super.start();
        }
        @Override
        public Future<?> stop() {
          return client.close();
        }
      }, new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD))
      .await();
    assertWaitUntil(() -> inflight.get() == numReq);
    vertx.undeploy(deploymentID).await();
    assertEquals(threads, interruptedThreads);
  }

  @Test
  public void testVirtualThreadsNotAvailable() {
    Assume.assumeFalse(isVirtualThreadAvailable());
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() {
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD)).onComplete(onFailure(err -> {
      testComplete();
    }));
    await();
  }
}
