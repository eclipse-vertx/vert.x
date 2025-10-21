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

import io.vertx.core.http.impl.HttpClientStream;
import io.vertx.core.http.impl.headers.HttpHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.tracing.TracingPolicy;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface Http2ClientStream extends Http2Stream {

  static Http2ClientStream create(Http2ClientConnection connection, ContextInternal context,
                                  TracingPolicy tracingPolicy, boolean decompressionSupported,
                                  ClientMetrics clientMetrics) {
    return new DefaultHttp2ClientStream(connection, context, tracingPolicy, decompressionSupported, clientMetrics);
  }

  static Http2ClientStream create(int id, Http2ClientConnection connection, ContextInternal context,
                                  TracingPolicy tracingPolicy, boolean decompressionSupported,
                                  ClientMetrics clientMetrics, boolean writable) {
    return new DefaultHttp2ClientStream(id, connection, context, tracingPolicy, decompressionSupported, clientMetrics, writable);
  }

  void upgrade(Object metric, Object trace);

  void onPush(Http2ClientStream pushStream, int promisedStreamId, HttpHeaders headers, boolean writable);

  HttpClientStream unwrap();

}
