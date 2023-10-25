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
package io.vertx.core;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.impl.VertxInternal;
import io.vertx.test.core.VertxTestBase;
import junit.framework.AssertionFailedError;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
    Assume.assumeTrue(VertxInternal.isVirtualThreadAvailable());
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
    Assume.assumeTrue(VertxInternal.isVirtualThreadAvailable());
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() {
        Future<String> fut = vertx.executeBlocking(() -> {
          assertTrue(isVirtual(Thread.currentThread()));
          return Thread.currentThread().getName();
        });
        String res = fut.await();
        assertNotSame(Thread.currentThread().getName(), res);
        testComplete();
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD));
    await();
  }

  @Test
  public void testDeployHTTPServer() throws Exception {
    Assume.assumeTrue(VertxInternal.isVirtualThreadAvailable());
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
          server.listen(8080, "localhost").await();
        }
      }, new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD))
      .toCompletionStage()
      .toCompletableFuture()
      .get();
    HttpClient client = vertx.createHttpClient();
    int numReq = 10;
    waitFor(numReq);
    for (int i = 0;i < numReq;i++) {
      Future<Buffer> resp = client.request(HttpMethod.GET, 8080, "localhost", "/")
        .compose(req -> req.send()
          .compose(HttpClientResponse::body));
      resp.onComplete(onSuccess(v -> complete()));
    }
    await();
    Assert.assertEquals(5, max.get());
  }

  @Test
  public void testVirtualThreadsNotAvailable() {
    Assume.assumeFalse(VertxInternal.isVirtualThreadAvailable());
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
