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
package io.vertx.core.http;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.test.core.VertxTestBase;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class VirtualThreadHttpTest extends VertxTestBase {

  private VertxInternal vertx;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    vertx = (VertxInternal) super.vertx;
  }

  @Test
  public void testHttpClient1() throws Exception {
    Assume.assumeTrue(isVirtualThreadAvailable());
    HttpServer server = vertx.createHttpServer();
    server.requestHandler(req -> {
      req.response().end("Hello World");
    });
    server.listen(8088, "localhost").toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    vertx.createVirtualThreadContext().runOnContext(v -> {
      HttpClient client = vertx.createHttpClient();
      for (int i = 0; i < 100; ++i) {
        HttpClientRequest req = Future.await(client.request(HttpMethod.GET, 8088, "localhost", "/"));
        HttpClientResponse resp = Future.await(req.send());
        Buffer body = Future.await(resp.body());
        String bodyString = body.toString(StandardCharsets.UTF_8);
        assertEquals("Hello World", body.toString());
      }
      testComplete();
    });
    await();
  }

  @Test
  public void testHttpClient2() throws Exception {
    Assume.assumeTrue(isVirtualThreadAvailable());
    waitFor(100);
    HttpServer server = vertx.createHttpServer();
    server.requestHandler(req -> {
      req.response().end("Hello World");
    });
    server.listen(8088, "localhost").toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    HttpClient client = vertx.createHttpClient();
    vertx.createVirtualThreadContext().runOnContext(v -> {
      for (int i = 0; i < 100; ++i) {
        client.request(HttpMethod.GET, 8088, "localhost", "/").onSuccess(req -> {
          HttpClientResponse resp = Future.await(req.send());
          StringBuffer body = new StringBuffer();
          resp.handler(buff -> {
            body.append(buff.toString());
          });
          resp.endHandler(v2 -> {
            assertEquals("Hello World", body.toString());
            complete();
          });
        });
      }
    });
    try {
      await();
    } finally {
      server.close().toCompletionStage().toCompletableFuture().get();
      client.close().toCompletionStage().toCompletableFuture().get();
    }
  }

  @Test
  public void testHttpClientTimeout() throws Exception {
    Assume.assumeTrue(isVirtualThreadAvailable());
    HttpServer server = vertx.createHttpServer();
    server.requestHandler(req -> {
    });
    server.listen(8088, "localhost").toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    vertx.createVirtualThreadContext().runOnContext(v -> {
      HttpClient client = vertx.createHttpClient();
      ContextInternal ctx = vertx.getOrCreateContext();
      HttpClientRequest req = Future.await(client.request(HttpMethod.GET, 8088, "localhost", "/"));
      PromiseInternal<HttpClientResponse> promise = ctx.promise();
      req.send().onComplete(promise);
      Exception failure = new Exception("Too late");
      vertx.setTimer(500, id -> promise.tryFail(failure));
      try {
        HttpClientResponse resp = Future.await(promise.future());
      } catch (Exception e) {
        assertSame(failure, e);
        testComplete();
      }
    });
    await();
  }
}
