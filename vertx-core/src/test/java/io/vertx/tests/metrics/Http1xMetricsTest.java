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

import io.vertx.core.ThreadingModel;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.NetClient;
import io.vertx.test.fakemetrics.FakeHttpServerMetrics;
import io.vertx.test.fakemetrics.FakeMetricsBase;
import io.vertx.test.fakemetrics.HttpServerMetric;
import io.vertx.test.http.HttpConfigurator;
import io.vertx.test.core.TestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class Http1xMetricsTest extends HttpMetricsTestBase {

  public Http1xMetricsTest() {
    this(ThreadingModel.EVENT_LOOP);
  }

  protected Http1xMetricsTest(ThreadingModel threadingModel) {
    super(HttpConfigurator.Http1x.DEFAULT, HttpVersion.HTTP_1_1, threadingModel);
  }

  @Test
  public void testAllocatedStreamResetShouldNotCallMetricsLifecycle() throws Exception {
    server.requestHandler(req -> {
      Assert.fail();
    });
    startServer(testAddress);
    CountDownLatch latch = new CountDownLatch(1);
    client = vertx.createHttpClient(new HttpClientOptions().setIdleTimeout(2));
    client.request(requestOptions).onComplete(TestUtils.onSuccess(req -> {
      req.exceptionHandler(err -> {
        latch.countDown();
      });
      req.connection().close();
    }));
    TestUtils.awaitLatch(latch);
  }

  @Test
  public void testActiveRequestMetricCleanedUpAfterEarlyResponseAndConnectionClose() throws Exception {
    AtomicReference<HttpServerMetric> metricRef = new AtomicReference<>();
    server.requestHandler(req -> {
      FakeHttpServerMetrics metrics = FakeMetricsBase.httpMetricsOf(server);
      metricRef.set(metrics.getRequestMetric(req));
      req.response().setStatusCode(401).end();
    });
    startServer(testAddress);

    CountDownLatch latch = new CountDownLatch(1);
    NetClient netClient = vertx.createNetClient();
    try {
      netClient.connect(testAddress.port(), "127.0.0.1").onSuccess(socket -> {
        StringBuilder received = new StringBuilder();
        socket.handler(buf -> {
          received.append(buf.toString());
          if (received.toString().contains("\r\n\r\n")) {
            socket.handler(null);
            socket.close().onComplete(v -> latch.countDown());
          }
        });
        socket.write(
          "GET / HTTP/1.1\r\n" +
          "Host: 127.0.0.1\r\n" +
          "Content-Length: 1\r\n" +
          "Connection: keep-alive\r\n" +
          "\r\n"
        );
      });
      latch.await();
      // responseEnd is the correct terminal call: the 401 response completed normally
      assertWaitUntil(() -> metricRef.get() != null && metricRef.get().responseEnded.get());
      Assert.assertFalse(metricRef.get().failed.get());
    } finally {
      netClient.close();
    }
  }

  @Test
  public void testActiveRequestMetricCleanedUpAfterConnectionCloseBeforeBodyComplete() throws Exception {
    AtomicReference<HttpServerMetric> metricRef = new AtomicReference<>();
    server.requestHandler(req -> {
      FakeHttpServerMetrics metrics = FakeMetricsBase.httpMetricsOf(server);
      metricRef.set(metrics.getRequestMetric(req));
      // Do not respond — body will never arrive
    });
    startServer(testAddress);

    NetClient netClient = vertx.createNetClient();
    try {
      netClient.connect(testAddress.port(), "127.0.0.1").onSuccess(socket ->
        socket.write(
          "POST / HTTP/1.1\r\n" +
          "Host: 127.0.0.1\r\n" +
          "Content-Length: 10\r\n" +
          "\r\n"
        ).onComplete(v -> socket.close())
      );
      // requestReset is the correct terminal call: connection closed before any response was sent
      assertWaitUntil(() -> metricRef.get() != null && metricRef.get().failed.get());
      Assert.assertFalse(metricRef.get().responseEnded.get());
    } finally {
      netClient.close();
    }
  }
}
