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

package io.vertx.test.fakemetrics;

import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.core.spi.observability.HttpResponse;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertNotNull;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class EndpointMetric implements ClientMetrics<HttpClientMetric, Void, HttpRequest, HttpResponse> {

  public final AtomicInteger queueSize = new AtomicInteger();
  public final AtomicInteger connectionCount = new AtomicInteger();
  public final AtomicInteger requestCount = new AtomicInteger();
  public final ConcurrentMap<HttpRequest, HttpClientMetric> requests = new ConcurrentHashMap<>();

  public EndpointMetric() {
  }

  @Override
  public Void enqueueRequest() {
    queueSize.incrementAndGet();
    return null;
  }

  @Override
  public void dequeueRequest(Void taskMetric) {
    queueSize.decrementAndGet();
  }

  @Override
  public HttpClientMetric requestBegin(String uri, HttpRequest request) {
    requestCount.incrementAndGet();
    HttpClientMetric metric = new HttpClientMetric(this, request);
    requests.put(request, metric);
    return metric;
  }

  @Override
  public void requestEnd(HttpClientMetric requestMetric, long bytesWritten) {
    if (requestMetric == null) {
      FakeHttpClientMetrics.unexpectedError = new RuntimeException("Unexpected null request metric");
      return;
    }
    requestMetric.requestEnded.incrementAndGet();
    requestMetric.bytesWritten.set(bytesWritten);
  }

  @Override
  public void responseBegin(HttpClientMetric requestMetric, HttpResponse response) {
    if (requestMetric == null) {
      FakeHttpClientMetrics.unexpectedError = new RuntimeException("Unexpected null request metric");
      return;
    }
    if (response == null) {
      FakeHttpClientMetrics.unexpectedError = new RuntimeException("Unexpected null response");
      return;
    }
    requestMetric.responseBegin.incrementAndGet();
  }

  @Override
  public void requestReset(HttpClientMetric requestMetric) {
    if (requestMetric == null) {
      FakeHttpClientMetrics.unexpectedError = new RuntimeException("Unexpected null request metric");
      return;
    }
    requestCount.decrementAndGet();
    requestMetric.failed.set(true);
    requests.remove(requestMetric.request);
  }

  @Override
  public void responseEnd(HttpClientMetric requestMetric, long bytesRead) {
    requestMetric.bytesRead.set(bytesRead);
    requestCount.decrementAndGet();
    requests.remove(requestMetric.request);
  }
}
