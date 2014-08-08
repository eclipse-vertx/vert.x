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

package io.vertx.core.metrics;

import com.codahale.metrics.RatioGauge;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.impl.VertxInternal;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class HttpClientMetrics extends HttpMetrics {

  private Map<HttpClientRequest, TimedContext> timings;
  private Map<HttpClientResponse, HttpClientRequest> requestsToResponses;

  public HttpClientMetrics(VertxInternal vertx, HttpClient client, HttpClientOptions options) {
    super(vertx, instanceName("io.vertx.http.clients", client));
    int maxPoolSize = options.getMaxPoolSize();
    if (isEnabled()) {
      // request timings
      timings = new WeakHashMap<>();
      requestsToResponses = new WeakHashMap<>();

      // max pool size gauge
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
  }

  public void beginRequest(HttpClientRequest request) {
    if (!isEnabled()) return;

    timings.put(request, time(null, null));
  }

  // This maps the response to a request so we can later complete our timing
  public void beginResponse(HttpClientRequest request, HttpClientResponse response) {
    if (!isEnabled()) return;

    requestsToResponses.put(response, request);
  }

  public void cancel(HttpClientRequest request) {
    if (!isEnabled()) return;

    timings.remove(request);
  }

  public void endResponse(HttpClientResponse response) {
    if (!isEnabled()) return;

    HttpClientRequest req = requestsToResponses.remove(response);
    TimedContext ctx = (req == null) ? null : timings.remove(req);
    if (ctx != null) {
      ctx.stop();
    }
  }
}
