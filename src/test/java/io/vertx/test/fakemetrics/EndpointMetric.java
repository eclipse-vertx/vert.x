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

import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.spi.metrics.ClientMetrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertNotNull;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class EndpointMetric implements ClientMetrics<HttpClientMetric, Void, HttpClientRequest, HttpClientResponse> {

  public final AtomicInteger queueSize = new AtomicInteger();
  public final AtomicInteger connectionCount = new AtomicInteger();
  public final AtomicInteger requestCount = new AtomicInteger();
  public final ConcurrentMap<HttpClientRequest, HttpClientMetric> requests = new ConcurrentHashMap<>();

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
  public HttpClientMetric requestBegin(String uri, HttpClientRequest request) {
    requestCount.incrementAndGet();
    HttpClientMetric metric = new HttpClientMetric(this, request);
    requests.put(request, metric);
    return metric;
  }

  @Override
  public void requestEnd(HttpClientMetric requestMetric) {
    requestMetric.requestEnded.incrementAndGet();
  }

  @Override
  public void responseBegin(HttpClientMetric requestMetric, HttpClientResponse response) {
    assertNotNull(response);
    requestMetric.responseBegin.incrementAndGet();
  }

  @Override
  public void requestReset(HttpClientMetric requestMetric) {
    requestCount.decrementAndGet();
    requestMetric.failed.set(true);
    requests.remove(requestMetric.request);
  }

  @Override
  public void responseEnd(HttpClientMetric requestMetric, HttpClientResponse response) {
    requestCount.decrementAndGet();
    requests.remove(requestMetric.request);
  }

}
