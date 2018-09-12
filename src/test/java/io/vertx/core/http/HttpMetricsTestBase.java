/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http;

import io.vertx.core.Context;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.test.core.AsyncTestBase;
import io.vertx.test.core.TestUtils;
import io.vertx.test.fakemetrics.FakeHttpClientMetrics;
import io.vertx.test.fakemetrics.FakeHttpServerMetrics;
import io.vertx.test.fakemetrics.FakeMetricsBase;
import io.vertx.test.fakemetrics.FakeMetricsFactory;
import io.vertx.test.fakemetrics.HttpClientMetric;
import io.vertx.test.fakemetrics.HttpServerMetric;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class HttpMetricsTestBase extends HttpTestBase {

  private final HttpVersion protocol;

  public HttpMetricsTestBase(HttpVersion protocol) {
    this.protocol = protocol;
  }

  protected HttpServerOptions createBaseServerOptions() {
    return new HttpServerOptions().setPort(DEFAULT_HTTP_PORT).setHost(DEFAULT_HTTP_HOST);
  }

  protected HttpClientOptions createBaseClientOptions() {
    return new HttpClientOptions();
  }

  @Override
  protected VertxOptions getOptions() {
    VertxOptions options = super.getOptions();
    options.setMetricsOptions(new MetricsOptions().setEnabled(true).setFactory(new FakeMetricsFactory()));
    return options;
  }

  @Test
  public void testHttpMetricsLifecycle() throws Exception {
    int numBuffers = 10;
    int chunkSize = 1000;
    int contentLength = numBuffers * chunkSize;
    AtomicReference<HttpServerMetric> serverMetric = new AtomicReference<>();
    server.requestHandler(req -> {
      assertEquals(protocol, req.version());
      FakeHttpServerMetrics serverMetrics = FakeMetricsBase.getMetrics(server);
      assertNotNull(serverMetrics);
      serverMetric.set(serverMetrics.getMetric(req));
      assertNotNull(serverMetric.get());
      assertNotNull(serverMetric.get().socket);
      assertNull(serverMetric.get().response.get());
      assertTrue(serverMetric.get().socket.connected.get());
      req.bodyHandler(buff -> {
        assertEquals(contentLength, buff.length());
        HttpServerResponse resp = req.response().setChunked(true);
        AtomicInteger numBuffer = new AtomicInteger(numBuffers);
        vertx.setPeriodic(1, timerID -> {
          Buffer chunk = TestUtils.randomBuffer(chunkSize);
          if (numBuffer.decrementAndGet() == 0) {
            resp.end(chunk);
            assertNull(serverMetrics.getMetric(req));
            vertx.cancelTimer(timerID);
          } else {
            resp.write(chunk);
            assertSame(serverMetric.get().response.get(), resp);
          }
        });
      });
    });
    startServer();
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<HttpClientMetric> clientMetric = new AtomicReference<>();
    FakeHttpClientMetrics metrics = FakeMetricsBase.getMetrics(client);
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v -> {
      HttpClientRequest req = client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath").exceptionHandler(this::fail);
      assertNull(metrics.getMetric(req));
      req.setChunked(true).handler(resp -> {
        clientMetric.set(metrics.getMetric(req));
        assertNotNull(clientMetric.get());
        assertNotNull(clientMetric.get().socket);
        assertTrue(clientMetric.get().socket.connected.get());
        resp.bodyHandler(buff -> {
          assertNull(metrics.getMetric(req));
          assertEquals(contentLength, buff.length());
          latch.countDown();
        });
      });
      for (int i = 0;i < numBuffers;i++) {
        req.write(TestUtils.randomBuffer(chunkSize));
      }
      req.end();
    });
    awaitLatch(latch);
    client.close();
    AsyncTestBase.assertWaitUntil(() -> !serverMetric.get().socket.connected.get());
    AsyncTestBase.assertWaitUntil(() -> contentLength == serverMetric.get().socket.bytesRead.get());
    AsyncTestBase.assertWaitUntil(() -> contentLength  == serverMetric.get().socket.bytesWritten.get());
    AsyncTestBase.assertWaitUntil(() -> !clientMetric.get().socket.connected.get());
    assertEquals(contentLength, clientMetric.get().socket.bytesRead.get());
    assertEquals(contentLength, clientMetric.get().socket.bytesWritten.get());
  }

  @Test
  public void testHttpClientLifecycle() throws Exception {
    CountDownLatch requestBeginLatch = new CountDownLatch(1);
    CountDownLatch requestBodyLatch = new CountDownLatch(1);
    CountDownLatch requestEndLatch = new CountDownLatch(1);
    CompletableFuture<Void> beginResponse = new CompletableFuture<>();
    CompletableFuture<Void> endResponse = new CompletableFuture<>();
    server.requestHandler(req -> {
      assertEquals(protocol, req.version());
      requestBeginLatch.countDown();
      req.handler(buff -> {
        requestBodyLatch.countDown();
      });
      req.endHandler(v -> {
        requestEndLatch.countDown();
      });
      Context ctx = vertx.getOrCreateContext();
      beginResponse.thenAccept(v1 -> {
        ctx.runOnContext(v2 -> {
          req.response().setChunked(true).write(TestUtils.randomAlphaString(1024));
        });
      });
      endResponse.thenAccept(v1 -> {
        ctx.runOnContext(v2 -> {
          req.response().end();
        });
      });
    });
    CountDownLatch listenLatch = new CountDownLatch(1);
    server.listen(8080, "localhost", onSuccess(s -> { listenLatch.countDown(); }));
    awaitLatch(listenLatch);
    FakeHttpClientMetrics clientMetrics = FakeMetricsBase.getMetrics(client);
    CountDownLatch responseBeginLatch = new CountDownLatch(1);
    CountDownLatch responseEndLatch = new CountDownLatch(1);
    HttpClientRequest req = client.post(8080, "localhost", "/somepath", resp -> {
      responseBeginLatch.countDown();
      resp.endHandler(v -> {
        responseEndLatch.countDown();
      });
    }).setChunked(true);
    req.sendHead();
    awaitLatch(requestBeginLatch);
    HttpClientMetric reqMetric = clientMetrics.getMetric(req);
    assertEquals(0, reqMetric.requestEnded.get());
    assertEquals(0, reqMetric.responseBegin.get());
    req.write(TestUtils.randomAlphaString(1024));
    awaitLatch(requestBodyLatch);
    assertEquals(0, reqMetric.requestEnded.get());
    assertEquals(0, reqMetric.responseBegin.get());
    req.end();
    awaitLatch(requestEndLatch);
    assertEquals(1, reqMetric.requestEnded.get());
    assertEquals(0, reqMetric.responseBegin.get());
    beginResponse.complete(null);
    awaitLatch(responseBeginLatch);
    assertEquals(1, reqMetric.requestEnded.get());
    assertEquals(1, reqMetric.responseBegin.get());
    endResponse.complete(null);
    awaitLatch(responseEndLatch);
    assertNull(clientMetrics.getMetric(req));
    assertEquals(1, reqMetric.requestEnded.get());
    assertEquals(1, reqMetric.responseBegin.get());
  }

  @Test
  public void testClientConnectionClosed() throws Exception {
    server.requestHandler(req -> {
      req.response().setChunked(true).write(Buffer.buffer("some-data"));
    });
    startServer();
    client = vertx.createHttpClient(createBaseClientOptions().setIdleTimeout(2));
    FakeHttpClientMetrics metrics = FakeMetricsBase.getMetrics(client);
    HttpClientRequest req = client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath");
    req.handler(resp -> {
      HttpClientMetric metric = metrics.getMetric(req);
      assertNotNull(metric);
      assertFalse(metric.failed.get());
      resp.exceptionHandler(err -> {
        assertNull(metrics.getMetric(req));
        assertTrue(metric.failed.get());
        testComplete();
      });
    });
    req.end();
    await();
  }

  @Test
  public void testServerConnectionClosed() throws Exception {
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().setIdleTimeout(2));
    server.requestHandler(req -> {
      FakeHttpServerMetrics metrics = FakeMetricsBase.getMetrics(server);
      HttpServerMetric metric = metrics.getMetric(req);
      assertNotNull(metric);
      assertFalse(metric.failed.get());
      req.response().closeHandler(v -> {
        assertNull(metrics.getMetric(req));
        assertTrue(metric.failed.get());
        testComplete();
      });
    });
    startServer();
    HttpClientRequest req = client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath");
    req.handler(resp -> {
    }).end();
    await();
  }
}
