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
package io.vertx.core.http.impl.observability;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.headers.HttpHeaders;
import io.vertx.core.http.impl.headers.HttpRequestHeaders;
import io.vertx.core.http.impl.headers.HttpResponseHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.TransportMetrics;
import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.core.spi.observability.HttpResponse;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

/**
 * Encapsulate observability state and interactions with the observability layer.
 */
public abstract class StreamObserver {

  private final TransportMetrics transportMetrics;
  final Object socketMetric;
  final HttpVersion version;
  final ContextInternal context;
  final SocketAddress remoteAddress;
  final TracingPolicy tracingPolicy;
  final VertxTracer tracer;
  Object metric;
  Object trace;
  HttpRequest observableRequest;
  HttpResponse observableResponse;

  public StreamObserver(ContextInternal context, SocketAddress remoteAddress, HttpVersion version,
                        TransportMetrics transportMetrics, Object socketMetric, TracingPolicy tracingPolicy, VertxTracer tracer) {
    this.context = context;
    this.transportMetrics = transportMetrics;
    this.socketMetric = socketMetric;
    this.remoteAddress = remoteAddress;
    this.version = version;
    this.tracingPolicy = tracingPolicy;
    this.tracer = tracer;
  }

  public Object metric() {
    return metric;
  }

  public Object trace() {
    return trace;
  }

  public abstract void observeReset();

  public abstract void observeInboundHeaders(HttpHeaders headers);

  public void observeOutboundTrailers(long bytesWritten) {
    if (transportMetrics != null) {
      transportMetrics.bytesWritten(socketMetric, remoteAddress, bytesWritten);
    }
  }

  public abstract void observeOutboundHeaders(HttpHeaders headers);

  public void observeInboundTrailers(long bytesRead) {
    if (transportMetrics != null) {
      transportMetrics.bytesRead(socketMetric, remoteAddress, bytesRead);
    }
  }

  HttpRequest observableRequest(HttpRequestHeaders requestHeaders, SocketAddress remoteAddress) {
    if (observableRequest == null) {
      observableRequest = new HttpRequest() {
        @Override
        public long id() {
          return 1L;
        }

        @Override
        public HttpVersion version() {
          return version;
        }

        @Override
        public String uri() {
          return requestHeaders.path();
        }

        @Override
        public String absoluteURI() {
          return requestHeaders.absoluteUri();
        }

        @Override
        public HttpMethod method() {
          return requestHeaders.method();
        }

        @Override
        public MultiMap headers() {
          return requestHeaders;
        }

        @Override
        public SocketAddress remoteAddress() {
          return remoteAddress;
        }
      };
    }
    return observableRequest;
  }

  HttpResponse observableResponse(HttpResponseHeaders responseHeaders, SocketAddress remoteAddress) {
    if (observableResponse == null) {
      observableResponse = new HttpResponse() {
        @Override
        public int statusCode() {
          return responseHeaders.status();
        }

        @Override
        public MultiMap headers() {
          return responseHeaders;
        }
      };
    }
    return observableResponse;
  }
}
