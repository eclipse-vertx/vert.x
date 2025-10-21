/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl.http2;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.impl.HttpServerStream;
import io.vertx.core.http.impl.headers.HttpHeaders;
import io.vertx.core.http.impl.headers.HttpRequestHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.tracing.TracingPolicy;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface Http2ServerStream extends Http2Stream {

  static Http2ServerStream create(Http2ServerConnection connection,
                                  HttpServerMetrics serverMetrics,
                                  Object socketMetric,
                                  ContextInternal context,
                                  HttpRequestHeaders requestHeaders,
                                  HttpMethod method,
                                  String uri,
                                  TracingPolicy tracingPolicy,
                                  int promisedId) {
    return new DefaultHttp2ServerStream(
      connection,
      serverMetrics,
      socketMetric,
      context,
      requestHeaders,
      method,
      uri,
      tracingPolicy,
      promisedId);
  }

  static Http2ServerStream create(
    Http2ServerConnection connection,
    HttpServerMetrics serverMetrics,
    Object socketMetric,
    ContextInternal context,
    TracingPolicy tracingPolicy) {
    return new DefaultHttp2ServerStream(connection, serverMetrics, socketMetric, context, tracingPolicy);
  }

  HttpHeaders headers();

  HttpServerStream unwrap();

}
