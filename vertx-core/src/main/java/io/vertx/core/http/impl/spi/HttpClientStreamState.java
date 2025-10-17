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

import io.vertx.core.http.impl.HttpClientStream;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.tracing.TracingPolicy;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpClientStreamState extends HttpStreamState {

  static HttpClientStreamState create(HttpClientConnectionProvider connection, ContextInternal context,
                                      TracingPolicy tracingPolicy, boolean decompressionSupported,
                                      ClientMetrics clientMetrics) {
    return new DefaultHttpClientStreamState(connection, context, tracingPolicy, decompressionSupported, clientMetrics);
  }

  static HttpClientStreamState create(int id, HttpClientConnectionProvider connection, ContextInternal context,
                                      TracingPolicy tracingPolicy, boolean decompressionSupported,
                                      ClientMetrics clientMetrics, boolean writable) {
    return new DefaultHttpClientStreamState(id, connection, context, tracingPolicy, decompressionSupported, clientMetrics, writable);
  }

  void upgrade(Object metric, Object trace);

  void onPush(HttpClientStreamState pushStream, int promisedStreamId, Http2HeadersMultiMap headers, boolean writable);

  HttpClientStream unwrap();

}
