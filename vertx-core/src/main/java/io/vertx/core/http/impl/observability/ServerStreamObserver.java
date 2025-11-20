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

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.http.impl.headers.HttpHeaders;
import io.vertx.core.http.impl.headers.HttpRequestHeaders;
import io.vertx.core.http.impl.headers.HttpResponseHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

public class ServerStreamObserver extends StreamObserver {

  private final HttpServerMetrics serverMetrics;

  public ServerStreamObserver(ContextInternal context, HttpServerMetrics serverMetrics, VertxTracer tracer, Object socketMetric, TracingPolicy tracingPolicy, SocketAddress remoteAddress) {
    super(context, remoteAddress, serverMetrics, socketMetric, tracingPolicy, tracer);
    this.serverMetrics = serverMetrics;
  }

  public void observeOutboundHeaders(HttpHeaders headers) {
    if (serverMetrics != null) {
      serverMetrics.responseBegin(metric, observableResponse((HttpResponseHeaders) headers, remoteAddress));
    }
    VertxTracer tracer = context.tracer();
    Object trace = this.trace;
    if (tracer != null && trace != null) {
      tracer.sendResponse(context, observableResponse((HttpResponseHeaders) headers, remoteAddress), trace, null, HttpUtils.SERVER_RESPONSE_TAG_EXTRACTOR);
    }
  }

  public void observeInboundTrailers(long bytesRead) {
    super.observeInboundTrailers(bytesRead);
    if (serverMetrics != null) {
      serverMetrics.requestEnd(metric, observableRequest, bytesRead);
    }
  }

  public void observeInboundHeaders(HttpHeaders headers) {
    if (serverMetrics != null) {
      metric = serverMetrics.requestBegin(socketMetric, observableRequest((HttpRequestHeaders) headers, remoteAddress));
    }
    VertxTracer tracer = context.tracer();
    if (tracer != null) {
      trace = tracer.receiveRequest(context, SpanKind.RPC, tracingPolicy, headers, ((HttpRequestHeaders) headers).method().name(), headers, HttpRequestHeaders.SERVER_TAG_EXTRACTOR);
    }
  }

  public void observeOutboundTrailers(long bytesWritten) {
    super.observeOutboundTrailers(bytesWritten);
    if (serverMetrics != null) {
      serverMetrics.responseEnd(metric, observableResponse, bytesWritten);
    }
  }

  public void observeReset() {
    if (serverMetrics != null) {
      serverMetrics.requestReset(metric);
    }
    VertxTracer tracer = context.tracer();
    Object trace = this.trace;
    if (tracer != null && trace != null) {
      tracer.sendResponse(context, null, trace, HttpUtils.STREAM_CLOSED_EXCEPTION, HttpUtils.SERVER_RESPONSE_TAG_EXTRACTOR);
    }
  }

  public void observeRoute(String route) {
    if (serverMetrics != null) {
      serverMetrics.requestRouted(metric, route);
    }
  }

  public void observePush(HttpResponseHeaders headers, HttpMethod method, String uri) {
    if (serverMetrics != null) {
      metric = serverMetrics.responsePushed(socketMetric, method, uri, observableResponse(headers, remoteAddress));
    }
  }
}
