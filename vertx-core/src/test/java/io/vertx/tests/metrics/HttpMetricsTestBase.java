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

package io.vertx.tests.metrics;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.internal.http.HttpClientInternal;
import io.vertx.core.internal.http.HttpServerRequestInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.SocketAddress;
import io.vertx.test.core.AsyncTestBase;
import io.vertx.test.core.TestUtils;
import io.vertx.test.fakemetrics.FakeHttpClientMetrics;
import io.vertx.test.fakemetrics.FakeHttpServerMetrics;
import io.vertx.test.fakemetrics.FakeMetricsBase;
import io.vertx.test.fakemetrics.FakeMetricsFactory;
import io.vertx.test.fakemetrics.FakeTCPMetrics;
import io.vertx.test.fakemetrics.HttpClientMetric;
import io.vertx.test.fakemetrics.HttpServerMetric;
import io.vertx.test.fakemetrics.SocketMetric;
import io.vertx.test.http.HttpTestBase;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class HttpMetricsTestBase extends HttpTestBase {

  private final HttpVersion protocol;
  private final ThreadingModel threadingModel;

  public HttpMetricsTestBase(HttpVersion protocol, ThreadingModel threadingModel) {
    this.protocol = protocol;
    this.threadingModel = threadingModel;
  }

  @Override
  protected void startServer(SocketAddress bindAddress, Context context, HttpServer server) throws Exception {
    if (threadingModel == ThreadingModel.WORKER) {
      context = ((VertxInternal) vertx).createWorkerContext();
    }
    super.startServer(bindAddress, context, server);
  }

  @Override
  protected void tearDown() throws Exception {
    FakeMetricsBase.sanityCheck();
    super.tearDown();
  }

  @Override
  protected VertxOptions getOptions() {
    VertxOptions options = super.getOptions();
    options.setMetricsOptions(new MetricsOptions().setEnabled(true));
    return options;
  }

  @Override
  protected Vertx createVertx(VertxOptions options) {
    return Vertx.builder().with(options)
      .withMetrics(new FakeMetricsFactory())
      .build();
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
      HttpServerMetric metric = serverMetrics.getRequestMetric(req);
      serverMetric.set(metric);
      assertSame(((HttpServerRequestInternal)req).metric(), metric);
      assertNotNull(serverMetric.get());
      assertNotNull(serverMetric.get().socket);
      assertNull(serverMetric.get().response.get());
      assertTrue(serverMetric.get().socket.connected.get());
      assertNull(serverMetric.get().route.get());
      req.routed("/route/:param");
      // Worker can wait
      assertWaitUntil(() -> serverMetric.get().route.get() != null);
      assertEquals("/route/:param", serverMetric.get().route.get());
      req.bodyHandler(buff -> {
        assertEquals(contentLength, buff.length());
        assertTrue(serverMetric.get().requestEnded.get());
        assertEquals(contentLength, serverMetric.get().bytesRead.get());
        HttpServerResponse resp = req.response().setChunked(true);
        AtomicInteger numBuffer = new AtomicInteger(numBuffers);
        vertx.setPeriodic(1, timerID -> {
          Buffer chunk = TestUtils.randomBuffer(chunkSize);
          if (numBuffer.decrementAndGet() == 0) {
            resp
              .end(chunk)
              .onComplete(onSuccess(v -> {
                assertTrue(serverMetric.get().responseEnded.get());
                assertFalse(serverMetric.get().failed.get());
                assertEquals(contentLength, serverMetric.get().bytesWritten.get());
                assertNull(serverMetrics.getRequestMetric(req));
              }));
            vertx.cancelTimer(timerID);
          } else {
            resp
              .write(chunk)
              .onComplete(onSuccess(v -> {
                assertSame(serverMetric.get().response.get().headers(), resp.headers());
              }));
          }
        });
      });
    });
    startServer(testAddress);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<HttpClientMetric> clientMetric = new AtomicReference<>();
    AtomicReference<SocketMetric> clientSocketMetric = new AtomicReference<>();
    FakeHttpClientMetrics metrics = FakeMetricsBase.getMetrics(client);
    NetClient netClient = ((HttpClientInternal) client).netClient();
    FakeTCPMetrics tcpMetrics = FakeMetricsBase.getMetrics(netClient);
    assertSame(metrics, tcpMetrics);
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v -> {
      assertEquals(Collections.emptySet(), metrics.endpoints());
      client.request(new RequestOptions(requestOptions)
        .setURI(TestUtils.randomAlphaString(16)))
        .onComplete(onSuccess(req -> {
          req
            .response().onComplete(onSuccess(resp -> {
              clientSocketMetric.set(metrics.firstMetric(testAddress));
              assertNotNull(clientSocketMetric.get());
              assertEquals(Collections.singleton(testAddress.toString()), metrics.endpoints());
              clientMetric.set(metrics.getMetric(resp.request()));
              assertNotNull(clientMetric.get());
              assertEquals(contentLength, clientMetric.get().bytesWritten.get());
              // assertNotNull(clientMetric.get().socket);
              // assertTrue(clientMetric.get().socket.connected.get());
              assertEquals((Integer) 1, metrics.connectionCount(testAddress));
              resp.bodyHandler(buff -> {
                assertEquals(contentLength, clientMetric.get().bytesRead.get());
                assertNull(metrics.getMetric(resp.request()));
                assertEquals(contentLength, buff.length());
                latch.countDown();
              });
            }));
          req.exceptionHandler(this::fail)
            .setChunked(true);
          assertNull(metrics.getMetric(req));
          for (int i = 0;i < numBuffers;i++) {
            req.write(TestUtils.randomBuffer(chunkSize));
          }
          req.end();
      }));
    });
    awaitLatch(latch);
    client.close();
    AsyncTestBase.assertWaitUntil(() -> metrics.endpoints().isEmpty());
    assertEquals(null, metrics.connectionCount(DEFAULT_HTTP_HOST_AND_PORT));
    AsyncTestBase.assertWaitUntil(() -> !serverMetric.get().socket.connected.get());
    AsyncTestBase.assertWaitUntil(() -> contentLength == serverMetric.get().socket.bytesRead.get());
    AsyncTestBase.assertWaitUntil(() -> contentLength  == serverMetric.get().socket.bytesWritten.get());
    AsyncTestBase.assertWaitUntil(() -> !clientSocketMetric.get().connected.get());
    assertEquals(contentLength, clientSocketMetric.get().bytesRead.get());
    assertEquals(contentLength, clientSocketMetric.get().bytesWritten.get());
    for (Iterator<Long> it : Arrays.asList(clientSocketMetric.get().bytesReadEvents.iterator(), serverMetric.get().socket.bytesWrittenEvents.iterator())) {
      while (it.hasNext()) {
        long val = it.next();
        if (it.hasNext()) {
          assertEquals(4096, val);
        } else {
          assertTrue(val < 4096);
        }
      }
    }
  }

  @Test
  public void testHttpClientLifecycle() throws Exception {

    // The test cannot pass for HTTP/2 upgrade for now
    HttpClientOptions opts = createBaseClientOptions();
    if (opts.getProtocolVersion() == HttpVersion.HTTP_2 &&
      !opts.isSsl() &&
      opts.isHttp2ClearTextUpgrade()) {
      return;
    }

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
    startServer(testAddress);
    FakeHttpClientMetrics clientMetrics = FakeMetricsBase.getMetrics(client);
    CountDownLatch responseBeginLatch = new CountDownLatch(1);
    CountDownLatch responseEndLatch = new CountDownLatch(1);
    Future<HttpClientRequest> request = client.request(new RequestOptions()
      .setMethod(HttpMethod.POST)
      .setPort(HttpTestBase.DEFAULT_HTTP_PORT)
      .setHost("localhost")
      .setURI("/somepath")).onComplete(onSuccess(req -> {
      req
        .response().onComplete(onSuccess(resp -> {
          responseBeginLatch.countDown();
          resp.endHandler(v -> {
            responseEndLatch.countDown();
          });
        }));
      req.setChunked(true);
      req.writeHead();
    }));
    awaitLatch(requestBeginLatch);
    HttpClientMetric reqMetric = clientMetrics.getMetric(request.result());
    waitUntil(() -> reqMetric.requestEnded.get() == 0);
    waitUntil(() -> reqMetric.responseBegin.get() == 0);
    request.result().write(TestUtils.randomAlphaString(1024));
    awaitLatch(requestBodyLatch);
    assertEquals(0, reqMetric.requestEnded.get());
    assertEquals(0, reqMetric.responseBegin.get());
    request.result().end();
    awaitLatch(requestEndLatch);
    waitUntil(() -> reqMetric.requestEnded.get() == 1);
    assertEquals(0, reqMetric.responseBegin.get());
    beginResponse.complete(null);
    awaitLatch(responseBeginLatch);
    assertEquals(1, reqMetric.requestEnded.get());
    waitUntil(() -> reqMetric.responseBegin.get() == 1);
    endResponse.complete(null);
    awaitLatch(responseEndLatch);
    waitUntil(() -> clientMetrics.getMetric(request.result()) == null);
    assertEquals(1, reqMetric.requestEnded.get());
    assertEquals(1, reqMetric.responseBegin.get());
  }

  @Test
  public void testClientConnectionClosed() throws Exception {
    server.requestHandler(req -> {
      req.response().setChunked(true).write(Buffer.buffer("some-data"));
    });
    startServer(testAddress);
    client = vertx.createHttpClient(createBaseClientOptions().setIdleTimeout(1));
    FakeHttpClientMetrics metrics = FakeMetricsBase.getMetrics(client);
    AtomicReference<HttpClientMetric> ref = new AtomicReference<>();
    client.request(requestOptions).onComplete(onSuccess(req -> {
      req.send().onComplete(onSuccess(resp -> {
        HttpClientMetric metric = metrics.getMetric(resp.request());
        assertNotNull(metric);
        ref.set(metric);
        assertFalse(metric.failed.get());
        req.connection().closeHandler(v1 -> {
          vertx.runOnContext(v2 -> {
            assertNull(metrics.getMetric(resp.request()));
            testComplete();
          });
        });
      }));
    }));
    assertWaitUntil(() -> ref.get() != null && ref.get().failed.get());
    await();
  }

  @Test
  public void testServerConnectionClosed() throws Exception {
    server.close();
    server = vertx.createHttpServer(createBaseServerOptions().setIdleTimeout(1));
    server.requestHandler(req -> {
      FakeHttpServerMetrics metrics = FakeMetricsBase.getMetrics(server);
      HttpServerMetric metric = metrics.getRequestMetric(req);
      assertNotNull(metric);
      assertFalse(metric.failed.get());
      req.response().closeHandler(v -> {
        assertNull(metrics.getRequestMetric(req));
        assertTrue(metric.failed.get());
        testComplete();
      });
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setURI(TestUtils.randomAlphaString(16))).onComplete(onSuccess(HttpClientRequest::send));
    await();
  }

  @Test
  public void testRouteMetrics() throws Exception {
    server.requestHandler(req -> {
      FakeHttpServerMetrics metrics = FakeMetricsBase.getMetrics(server);
      HttpServerMetric metric = metrics.getRequestMetric(req);
      assertNull(metric.route.get());
      req.routed("MyRoute");
      // Worker can wait
      assertWaitUntil(() -> metric.route.get() != null);
      assertEquals("MyRoute", metric.route.get());
      metric.route.set(null);
      req.routed("MyRoute - rerouted");
      // Worker can wait
      assertWaitUntil(() -> metric.route.get() != null);
      assertEquals("MyRoute - rerouted", metric.route.get());
      req.response().end();
      testComplete();
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setURI(TestUtils.randomAlphaString(16))).onComplete(onSuccess(HttpClientRequest::send));
    await();
  }

  @Test
  public void testRouteMetricsIgnoredAfterResponseEnd() throws Exception {
    server.requestHandler(req -> {
      FakeHttpServerMetrics metrics = FakeMetricsBase.getMetrics(server);
      HttpServerMetric metric = metrics.getRequestMetric(req);
      assertNull(metric.route.get());
      req.response().end();
      req.routed("Routed after ending");
      assertNull(metric.route.get());
      testComplete();
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setURI(TestUtils.randomAlphaString(16))).onComplete(onSuccess(HttpClientRequest::send));
    await();
  }

  @Test
  public void testResetImmediately() {
    FakeHttpClientMetrics metrics = FakeMetricsBase.getMetrics(client);
    server.requestHandler(req -> {
    }).listen(testAddress).onComplete(onSuccess(v -> {
      client.request(requestOptions).onComplete(onSuccess(request -> {
        assertNull(metrics.getMetric(request));
        request.reset(0);
        vertx.setTimer(10, id -> {
          testComplete();
        });
      }));
    }));
    await();
  }
}
