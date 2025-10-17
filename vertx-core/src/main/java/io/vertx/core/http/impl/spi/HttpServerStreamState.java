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
package io.vertx.core.http.impl.spi;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.impl.HttpServerStream;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.tracing.TracingPolicy;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpServerStreamState extends HttpStreamState {

  static HttpServerStreamState create(HttpServerConnectionProvider connection,
                                      HttpServerMetrics serverMetrics,
                                      Object socketMetric,
                                      ContextInternal context,
                                      Http2HeadersMultiMap requestHeaders,
                                      HttpMethod method,
                                      String uri,
                                      TracingPolicy tracingPolicy,
                                      int promisedId) {
    return new DefaultHttpServerStreamState(
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

  static HttpServerStreamState create(
    HttpServerConnectionProvider connection,
    HttpServerMetrics serverMetrics,
    Object socketMetric,
    ContextInternal context,
    TracingPolicy tracingPolicy) {
    return new DefaultHttpServerStreamState(connection, serverMetrics, socketMetric, context, tracingPolicy);
  }

  Http2HeadersMultiMap headers();

  HttpServerStream unwrap();

}
