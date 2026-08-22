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
import io.vertx.core.http.HttpVersion;
import io.vertx.test.fakemetrics.FakeHttpServerMetrics;
import io.vertx.test.fakemetrics.FakeMetricsBase;
import io.vertx.test.fakemetrics.HttpServerMetric;
import io.vertx.test.http.HttpConfigurator;
import org.junit.Assert;
import org.junit.Test;

import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests for the HTTP/1 active-request metrics leak fixed in #6329.
 *
 * When the server ends the response before the request body is complete and the
 * client then closes the connection without sending the remaining body, the
 * request metric must be reset (requestReset) instead of leaking forever.
 */
public class Http1xMetricsLeakTest extends HttpMetricsTestBase {

  public Http1xMetricsLeakTest() {
    super(HttpConfigurator.Http1x.DEFAULT, HttpVersion.HTTP_1_1, ThreadingModel.EVENT_LOOP);
  }

  @Test
  public void testActiveRequestResetWhenConnectionClosesBeforeRequestBody() throws Exception {
    AtomicReference<HttpServerMetric> metricRef = new AtomicReference<>();
    server.requestHandler(req -> {
      FakeHttpServerMetrics metrics = FakeMetricsBase.httpMetricsOf(server);
      HttpServerMetric metric = metrics.getRequestMetric(req);
      Assert.assertNotNull(metric);
      metricRef.set(metric);
      // Early response: end before the request body is consumed
      req.response().setStatusCode(401).end();
    });
    startServer(testAddress);

    // Send a GET with Content-Length: 1 but close the connection without sending the body byte
    try (Socket socket = new Socket("127.0.0.1", testAddress.port())) {
      socket.setSoTimeout(2000);
      socket.getOutputStream().write((
        "GET / HTTP/1.1\r\n" +
        "Host: 127.0.0.1\r\n" +
        "Content-Length: 1\r\n" +
        "Connection: keep-alive\r\n" +
        "\r\n"
      ).getBytes());
      socket.getOutputStream().flush();
      // Read the early 401 response then close without sending the declared body byte
      while (socket.getInputStream().read() >= 0) {
        // consume
      }
    }

    // The request body was never completed, so requestReset must have been
    // reported and the metric must be marked as failed (i.e. removed/reset)
    assertWaitUntil(() -> metricRef.get() != null && metricRef.get().failed.get());
  }
}