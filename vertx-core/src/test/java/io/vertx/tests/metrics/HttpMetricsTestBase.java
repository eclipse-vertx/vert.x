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
import io.vertx.core.internal.http.HttpClientRequestInternal;
import io.vertx.core.internal.http.HttpServerRequestInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.test.core.AsyncTestBase;
import io.vertx.test.core.TestUtils;
import io.vertx.test.fakemetrics.*;
import io.vertx.test.http.HttpConfig;
import io.vertx.test.http.SimpleHttpTest;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class HttpMetricsTestBase extends SimpleHttpTest {

  private final HttpVersion protocol;
  private final ThreadingModel threadingModel;

  public HttpMetricsTestBase(HttpConfig config,  HttpVersion protocol, ThreadingModel threadingModel) {
    super(config, ReportMode.FORBIDDEN);
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
    String requestUri = TestUtils.randomAlphaString(16);
    int numBuffers = 10;
    int chunkSize = 1000;
    int contentLength = numBuffers * chunkSize;
    AtomicReference<HttpServerMetric> serverMetric = new AtomicReference<>();
    server.requestHandler(req -> {
      Assert.assertEquals(protocol, req.version());
      FakeHttpServerMetrics serverMetrics = FakeMetricsBase.httpMetricsOf(server);
      Assert.assertNotNull(serverMetrics);
      HttpServerMetric metric = serverMetrics.getRequestMetric(req);
      Assert.assertSame(((HttpServerRequestInternal)req).metric(), metric);
      Assert.assertNotNull(metric);
      Assert.assertEquals(requestUri, metric.uri);
      Assert.assertNotNull(metric.request);
      Assert.assertEquals(protocol, metric.request.version());
      Assert.assertNull(metric.response.get());
      Assert.assertNull(metric.route.get());
      req.routed("/route/:param");
      // Worker can wait
      assertWaitUntil(() -> metric.route.get() != null);
      serverMetric.set(metric);
      Assert.assertEquals("/route/:param", serverMetric.get().route.get());
      req.bodyHandler(buff -> {
        Assert.assertEquals(contentLength, buff.length());
        Assert.assertTrue(serverMetric.get().requestEnded.get());
        Assert.assertEquals(contentLength, serverMetric.get().bytesRead.get());
        HttpServerResponse resp = req.response().setChunked(true);
        AtomicInteger numBuffer = new AtomicInteger(numBuffers);
        vertx.setPeriodic(1, timerID -> {
          Buffer chunk = TestUtils.randomBuffer(chunkSize);
          if (numBuffer.decrementAndGet() == 0) {
            resp
              .end(chunk)
              .onComplete(TestUtils.onSuccess(v -> {
                Assert.assertTrue(serverMetric.get().responseEnded.get());
                Assert.assertFalse(serverMetric.get().failed.get());
                Assert.assertEquals(contentLength, serverMetric.get().bytesWritten.get());
                Assert.assertNull(serverMetrics.getRequestMetric(req));
              }));
            vertx.cancelTimer(timerID);
          } else {
            resp
              .write(chunk)
              .onComplete(TestUtils.onSuccess(v -> {
                Assert.assertSame(serverMetric.get().response.get().headers(), resp.headers());
              }));
          }
        });
      });
    });
    startServer(testAddress);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<HttpClientMetric> clientMetric = new AtomicReference<>();
    AtomicReference<ConnectionMetric> clientSocketMetric = new AtomicReference<>();
    FakeHttpClientMetrics clientMetrics = FakeMetricsBase.httpMetricsOf(client);
//    Http1xOrH2ChannelConnector connector = (Http1xOrH2ChannelConnector)((HttpClientInternal) client).channelConnector();
//    FakeTCPMetrics tcpMetrics = FakeMetricsBase.getMetrics(connector.netClient());
//    assertSame(metrics, tcpMetrics);
    Context ctx = vertx.getOrCreateContext();
    ctx.runOnContext(v -> {
      Assert.assertEquals(Collections.emptySet(), clientMetrics.endpoints());
      client.request(new RequestOptions(requestOptions)
        .setURI(requestUri))
        .onComplete(TestUtils.onSuccess(req -> {
          req
            .response().onComplete(TestUtils.onSuccess(resp -> {
              FakeTransportMetrics transportMetrics = FakeTCPMetrics.transportMetricsOf(client);
              clientSocketMetric.set(transportMetrics.firstMetric(testAddress));
              Assert.assertNotNull(clientSocketMetric.get());
              Assert.assertEquals(Collections.singleton(testAddress.toString()), clientMetrics.endpoints());
              clientMetric.set(clientMetrics.getMetric(resp.request()));
              Assert.assertNotNull(clientMetric.get());
              Assert.assertEquals(contentLength, clientMetric.get().bytesWritten.get());
              // assertNotNull(clientMetric.get().socket);
              // assertTrue(clientMetric.get().socket.connected.get());
              Assert.assertEquals((Integer) 1, transportMetrics.connectionCount(testAddress));
              resp.bodyHandler(buff -> {
                Assert.assertEquals(contentLength, clientMetric.get().bytesRead.get());
                Assert.assertNull(clientMetrics.getMetric(resp.request()));
                Assert.assertEquals(contentLength, buff.length());
                latch.countDown();
              });
            }));
          req.exceptionHandler(err -> Assert.fail(err.getMessage()))
            .setChunked(true);
          Assert.assertNull(clientMetrics.getMetric(req));
          for (int i = 0;i < numBuffers;i++) {
            req.write(TestUtils.randomBuffer(chunkSize));
          }
          req.end();
      }));
    });
    TestUtils.awaitLatch(latch);
    client.close();
    AsyncTestBase.assertWaitUntil(() -> clientMetrics.endpoints().isEmpty());
    Assert.assertEquals(null, clientMetrics.connectionCount(DEFAULT_HTTP_HOST_AND_PORT));
    AsyncTestBase.assertWaitUntil(() -> !clientSocketMetric.get().connected.get());
    Assert.assertEquals(contentLength, clientSocketMetric.get().bytesRead.get());
    Assert.assertEquals(contentLength, clientSocketMetric.get().bytesWritten.get());
  }

  @Test
  public void testHttpClientLifecycle() throws Exception {
    client.close().await();
    client = config.forClient().setMetricsName("the-metrics").create(vertx);
    CountDownLatch requestBeginLatch = new CountDownLatch(1);
    CountDownLatch requestBodyLatch = new CountDownLatch(1);
    CountDownLatch requestEndLatch = new CountDownLatch(1);
    CompletableFuture<Void> beginResponse = new CompletableFuture<>();
    CompletableFuture<Void> endResponse = new CompletableFuture<>();
    server.requestHandler(req -> {
      Assert.assertEquals(protocol, req.version());
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
    FakeHttpClientMetrics clientMetrics = FakeMetricsBase.httpMetricsOf(client);
    Assert.assertEquals("the-metrics", clientMetrics.name());
    CountDownLatch responseBeginLatch = new CountDownLatch(1);
    HttpClientRequest request = client.request(new RequestOptions(requestOptions)
      .setMethod(HttpMethod.POST)
      .setURI("/somepath")).await();
    HttpClientMetric initRequestMetric = (HttpClientMetric)((HttpClientRequestInternal)request).metric(); // Might be null
    Assert.assertTrue(initRequestMetric != null);
    Future<Void> responseEndLatch = request
      .response()
      .compose(response -> {
      responseBeginLatch.countDown();
      return response.end();
    });
    request.setChunked(true).writeHead().await();
    TestUtils.awaitLatch(requestBeginLatch);
    HttpClientMetric requestMetric = clientMetrics.getMetric(request);
    Assert.assertSame(initRequestMetric, requestMetric);
    waitUntil(() -> requestMetric.requestEnded.get() == 0);
    waitUntil(() -> requestMetric.responseBegin.get() == 0);
    request.write(TestUtils.randomAlphaString(1024));
    TestUtils.awaitLatch(requestBodyLatch);
    Assert.assertEquals(0, requestMetric.requestEnded.get());
    Assert.assertEquals(0, requestMetric.responseBegin.get());
    request.end();
    TestUtils.awaitLatch(requestEndLatch);
    waitUntil(() -> requestMetric.requestEnded.get() == 1);
    Assert.assertEquals(0, requestMetric.responseBegin.get());
    beginResponse.complete(null);
    TestUtils.awaitLatch(responseBeginLatch);
    Assert.assertEquals(1, requestMetric.requestEnded.get());
    waitUntil(() -> requestMetric.responseBegin.get() == 1);
    endResponse.complete(null);
    responseEndLatch.await();
    waitUntil(() -> clientMetrics.getMetric(request) == null);
    Assert.assertEquals(1, requestMetric.requestEnded.get());
    Assert.assertEquals(1, requestMetric.responseBegin.get());
  }

  @Test
  public void testClientConnectionClosed() throws Exception {
    server.requestHandler(req -> {
      req.response().setChunked(true).write(Buffer.buffer("some-data"));
    });
    startServer(testAddress);
    client.close().await();
    client = config.forClient().setIdleTimeout(Duration.ofSeconds(1)).create(vertx);
    FakeHttpClientMetrics metrics = FakeMetricsBase.httpMetricsOf(client);
    AtomicReference<HttpClientMetric> ref = new AtomicReference<>();
    client.request(requestOptions).onComplete(TestUtils.onSuccess(req -> {
      req.send().onComplete(TestUtils.onSuccess(resp -> {
        HttpClientMetric metric = metrics.getMetric(resp.request());
        Assert.assertNotNull(metric);
        ref.set(metric);
        Assert.assertFalse(metric.failed.get());
        resp.exceptionHandler(err -> {
          testComplete();
        });
      }));
    }));
    assertWaitUntil(() -> ref.get() != null && ref.get().failed.get());
    await();
  }

  @Test
  public void testServerConnectionClosed() throws Exception {
    server.close();
    server = config.forServer().setIdleTimeout(Duration.ofSeconds(1)).create(vertx);
    server.requestHandler(req -> {
      FakeHttpServerMetrics metrics = FakeMetricsBase.httpMetricsOf(server);
      HttpServerMetric metric = metrics.getRequestMetric(req);
      Assert.assertNotNull(metric);
      Assert.assertFalse(metric.failed.get());
      req.response().closeHandler(v -> {
        Assert.assertNull(metrics.getRequestMetric(req));
        Assert.assertTrue(metric.failed.get());
        testComplete();
      });
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setURI(TestUtils.randomAlphaString(16))).onComplete(TestUtils.onSuccess(HttpClientRequest::send));
    await();
  }

  @Test
  public void testRouteMetrics() throws Exception {
    server.requestHandler(req -> {
      FakeHttpServerMetrics metrics = FakeMetricsBase.httpMetricsOf(server);
      HttpServerMetric metric = metrics.getRequestMetric(req);
      Assert.assertNull(metric.route.get());
      req.routed("MyRoute");
      // Worker can wait
      assertWaitUntil(() -> metric.route.get() != null);
      Assert.assertEquals("MyRoute", metric.route.get());
      metric.route.set(null);
      req.routed("MyRoute - rerouted");
      // Worker can wait
      assertWaitUntil(() -> metric.route.get() != null);
      Assert.assertEquals("MyRoute - rerouted", metric.route.get());
      req.response().end();
      testComplete();
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setURI(TestUtils.randomAlphaString(16))).onComplete(TestUtils.onSuccess(HttpClientRequest::send));
    await();
  }

  @Test
  public void testRouteMetricsIgnoredAfterResponseEnd() throws Exception {
    server.requestHandler(req -> {
      FakeHttpServerMetrics metrics = FakeMetricsBase.httpMetricsOf(server);
      HttpServerMetric metric = metrics.getRequestMetric(req);
      Assert.assertNull(metric.route.get());
      req.response().end();
      req.routed("Routed after ending");
      Assert.assertNull(metric.route.get());
      testComplete();
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setURI(TestUtils.randomAlphaString(16))).onComplete(TestUtils.onSuccess(HttpClientRequest::send));
    await();
  }

  @Test
  public void testResetImmediately() {
    FakeHttpClientMetrics metrics = FakeMetricsBase.httpMetricsOf(client);
    server.requestHandler(req -> {
    }).listen(testAddress).onComplete(TestUtils.onSuccess(v -> {
      client.request(requestOptions).onComplete(TestUtils.onSuccess(request -> {
        Assert.assertNull(metrics.getMetric(request));
        request.reset(0);
        vertx.setTimer(10, id -> {
          testComplete();
        });
      }));
    }));
    await();
  }

  @Test
  public void testHttpClientMetricsQueueClose() throws Exception {
    List<Runnable> requests = Collections.synchronizedList(new ArrayList<>());
    Set<HttpConnection> closedConnections = new HashSet<>();
    server.requestHandler(req -> {
      requests.add(() -> {
        HttpConnection connection = req.connection();
        if (closedConnections.add(connection)) {
          vertx.runOnContext(v -> {
            connection.close();
          });
        }
      });
    });
    server.listen(testAddress).await();
    FakeHttpClientMetrics metrics = FakeHttpClientMetrics.httpMetricsOf(client);
    for (int i = 0;i < 5;i++) {
      client.request(requestOptions)
        .compose(HttpClientRequest::end)
        .onComplete(TestUtils.onSuccess(v -> {
        }));
    }
    assertWaitUntil(() -> requests.size() == 5);
    EndpointMetric endpoint = metrics.endpoint("localhost:" + testAddress.port());
    int expectedConnections = protocol == HttpVersion.HTTP_1_1 ? 5 : 1;
    Assert.assertEquals(expectedConnections, endpoint.connectionCount.get());
    ArrayList<Runnable> copy = new ArrayList<>(requests);
    requests.clear();
    copy.forEach(Runnable::run);
    assertWaitUntil(() -> metrics.endpoints().isEmpty());
    assertWaitUntil(() -> endpoint.connectionCount.get() == 0);
  }
}
