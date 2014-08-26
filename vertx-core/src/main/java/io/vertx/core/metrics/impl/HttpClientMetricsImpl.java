/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.metrics.impl;

import com.codahale.metrics.RatioGauge;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.metrics.spi.HttpClientMetrics;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
class HttpClientMetricsImpl extends HttpMetricsImpl implements HttpClientMetrics {

  private Map<HttpClientRequest, TimedContext> timings;
  private Map<HttpClientResponse, WeakReference<HttpClientRequest>> requestsToResponses;

  public HttpClientMetricsImpl(AbstractMetrics metrics, String baseName, HttpClientOptions options) {
    super(metrics, baseName);
    initialize(options);
  }

  public void initialize(HttpClientOptions options) {
    if (!isEnabled()) return;

    // request timings
    timings = new WeakHashMap<>();
    requestsToResponses = new WeakHashMap<>();

    // max pool size gauge
    int maxPoolSize = options.getMaxPoolSize();
    gauge(() -> maxPoolSize, "connections", "max-pool-size");

    // connection pool ratio
    RatioGauge gauge = new RatioGauge() {
      @Override
      protected Ratio getRatio() {
        return Ratio.of(connections(), maxPoolSize);
      }
    };
    gauge(gauge, "connections", "pool-ratio");
  }

  @Override
  public void requestBegin(HttpClientRequest request) {
    if (!isEnabled()) return;

    timings.put(request, time(null, null));
  }

  @Override
  public void requestEndAndResponseBegin(HttpClientRequest request, HttpClientResponse response) {
    if (!isEnabled()) return;

    // This maps the response to a request so we can later complete our timing
    requestsToResponses.put(response, new WeakReference<>(request));
  }

  @Override
  public void responseEnd(HttpClientResponse response) {
    if (!isEnabled()) return;

    WeakReference<HttpClientRequest> ref = requestsToResponses.remove(response);
    HttpClientRequest req = (ref == null) ? null : ref.get();
    TimedContext ctx = (req == null) ? null : timings.remove(req);
    if (ctx != null) {
      ctx.stop();
    }
  }
}
