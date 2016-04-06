/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.Context;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.test.fakemetrics.FakeHttpClientMetrics;
import io.vertx.test.fakemetrics.FakeHttpServerMetrics;
import io.vertx.test.fakemetrics.FakeMetricsBase;
import io.vertx.test.fakemetrics.FakeMetricsFactory;
import io.vertx.test.fakemetrics.HttpClientMetric;
import io.vertx.test.fakemetrics.HttpServerMetric;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpMetricsTest extends HttpTestBase {

  @BeforeClass
  public static void setFactory() {
    ConfigurableMetricsFactory.delegate = new FakeMetricsFactory();
  }

  @AfterClass
  public static void unsetFactory() {
    ConfigurableMetricsFactory.delegate = null;
  }

  @Override
  protected VertxOptions getOptions() {
    VertxOptions options = super.getOptions();
    options.setMetricsOptions(new MetricsOptions().setEnabled(true));
    return options;
  }

  @Test
  public void testHttp1MetricsLifecycle() throws Exception {
    testMetricsLifecycle(HttpVersion.HTTP_1_1);
  }

  @Test
  public void testHttp2MetricsLifecycle() throws Exception {
    testMetricsLifecycle(HttpVersion.HTTP_2);
  }

  private void testMetricsLifecycle(HttpVersion protocol) throws Exception {
    waitFor(2);
    int numBuffers = 10;
    int contentLength = numBuffers * 1000;
    AtomicReference<HttpServerMetric> serverMetric = new AtomicReference<>();
    server.requestHandler(req -> {
      FakeHttpServerMetrics serverMetrics = FakeMetricsBase.getMetrics(server);
      assertNotNull(serverMetrics);
      serverMetric.set(serverMetrics.getMetric(req));
      assertNotNull(serverMetric.get());
      assertNotNull(serverMetric.get().socket);
      assertTrue(serverMetric.get().socket.connected.get());
      req.bodyHandler(buff -> {
        assertEquals(contentLength, buff.length());
        HttpServerResponse resp = req.response().putHeader("Content-Length", "" + contentLength);
        AtomicInteger numBuffer = new AtomicInteger(numBuffers);
        vertx.setPeriodic(1, timerID -> {
          if (numBuffer.getAndDecrement() == 0) {
            resp.end();
            assertNull(serverMetrics.getMetric(req));
            vertx.cancelTimer(timerID);
          } else {
            resp.write(TestUtils.randomBuffer(1000));
          }
        });
      });
    });
    startServer();
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<HttpClientMetric> clientMetric = new AtomicReference<>();
    client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(protocol));
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
        req.write(TestUtils.randomBuffer(1000));
      }
      req.end();
    });
    awaitLatch(latch);
    client.close();
    waitUntil(() -> !serverMetric.get().socket.connected.get());
    assertEquals(contentLength, serverMetric.get().socket.bytesRead.get());
    assertEquals(contentLength, serverMetric.get().socket.bytesWritten.get());
    waitUntil(() -> !clientMetric.get().socket.connected.get());
    assertEquals(contentLength, clientMetric.get().socket.bytesRead.get());
    assertEquals(contentLength, clientMetric.get().socket.bytesWritten.get());
  }

  @Test
  public void testHttp1ClientConnectionClosed() throws Exception {
    testClientConnectionClosed(HttpVersion.HTTP_1_1);
  }

  @Test
  public void testHttp2ClientConnectionClosed() throws Exception {
    testClientConnectionClosed(HttpVersion.HTTP_2);
  }

  private void testClientConnectionClosed(HttpVersion protocol) throws Exception {
    server.requestHandler(req -> {
      req.response().setChunked(true).write(Buffer.buffer("some-data"));
    });
    startServer();
    client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(protocol).setIdleTimeout(2));
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
  public void testHttp1ServerConnectionClosed() throws Exception {
    testServerConnectionClosed(HttpVersion.HTTP_1_1);
  }

  @Test
  public void testHttp2ServerConnectionClosed() throws Exception {
    testServerConnectionClosed(HttpVersion.HTTP_2);
  }

  private void testServerConnectionClosed(HttpVersion protocol) throws Exception {
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions().setPort(DEFAULT_HTTP_PORT).setHost(DEFAULT_HTTP_HOST).setIdleTimeout(2));
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
    client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(protocol));
    HttpClientRequest req = client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath");
    req.handler(resp -> {
    }).end();
    await();
  }

  @Test
  public void testPushPromise() throws Exception {
    waitFor(2);
    int numBuffers = 10;
    int contentLength = numBuffers * 1000;
    server.requestHandler(req -> {
      req.response().push(HttpMethod.GET, "/wibble", ar -> {
        HttpServerResponse pushedResp = ar.result();
        FakeHttpServerMetrics serverMetrics = FakeMetricsBase.getMetrics(server);
        HttpServerMetric serverMetric = serverMetrics.getMetric(pushedResp);
        assertNotNull(serverMetric);
        pushedResp.putHeader("content-length", "" + contentLength);
        AtomicInteger numBuffer = new AtomicInteger(numBuffers);
        vertx.setPeriodic(1, timerID -> {
          if (numBuffer.getAndDecrement() == 0) {
            pushedResp.end();
            assertNull(serverMetrics.getMetric(pushedResp));
            vertx.cancelTimer(timerID);
            complete();
          } else {
            pushedResp.write(TestUtils.randomBuffer(1000));
          }
        });
      });
    });
    startServer();
    client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_2));
    FakeHttpClientMetrics metrics = FakeMetricsBase.getMetrics(client);
    HttpClientRequest req = client.get(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath", resp -> {
    });
    req.pushHandler(pushedReq -> {
      HttpClientMetric metric = metrics.getMetric(pushedReq);
      assertNotNull(metric);
      assertSame(pushedReq, metric.request);
      pushedReq.handler(resp -> {
        resp.endHandler(v -> {
          assertNull(metrics.getMetric(pushedReq));
          complete();
        });
      });
    });
    req.end();
    await();
  }
}
