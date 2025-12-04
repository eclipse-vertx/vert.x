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

import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.http.impl.headers.HttpHeaders;
import io.vertx.core.http.impl.headers.HttpRequestHeaders;
import io.vertx.core.http.impl.headers.HttpResponseHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.TransportMetrics;
import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.core.spi.observability.HttpResponse;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

import java.util.function.BiConsumer;

public class ClientStreamObserver extends StreamObserver {

  private final ClientMetrics<Object, HttpRequest, HttpResponse> clientMetrics;
  private Object trace;
  private HttpResponseHeaders inboundHeaders;

  public ClientStreamObserver(ContextInternal context, TracingPolicy tracingPolicy,
                              ClientMetrics<Object, HttpRequest, HttpResponse> clientMetrics, Object metric,
                              TransportMetrics<?> transportMetrics, Object socketMetric, VertxTracer tracer, SocketAddress remoteAddress) {
    super(context, remoteAddress, transportMetrics, socketMetric, tracingPolicy, tracer);
    this.clientMetrics = clientMetrics;
    this.metric = metric;
  }

  public void observePush(HttpRequestHeaders headers) {
    if (clientMetrics != null) {
      Object metric = clientMetrics.init();
      if (metric == null) {
        metric = clientMetrics.requestBegin(headers.path().toString(), observableRequest(headers, remoteAddress));
      } else {
        clientMetrics.requestBegin(metric, headers.path().toString(), observableRequest(headers, remoteAddress));
      }
      this.metric = metric;
      clientMetrics.requestEnd(metric, 0L);
    }
  }

  public void observeUpgrade(Object metric, Object trace) {
    this.metric = metric;
    this.trace = trace;
  }

  public void observeOutboundHeaders(HttpHeaders headers) {
    HttpRequestHeaders r = (HttpRequestHeaders) headers;
    if (clientMetrics != null) {
      Object m = metric;
      if (m == null) {
        metric = clientMetrics.requestBegin(r.path(), observableRequest(r, remoteAddress));
      } else {
        clientMetrics.requestBegin(metric, r.path(), observableRequest(r, remoteAddress));
      }
    }
    VertxTracer tracer = context.tracer();
    if (tracer != null) {
      BiConsumer<String, String> headers_ = (key, val) -> headers.add(key, val);
      String traceOperation = r.trace();
      if (traceOperation == null) {
        traceOperation = r.method().toString();
      }
      trace = tracer.sendRequest(context, SpanKind.RPC, tracingPolicy, r, traceOperation, headers_, HttpRequestHeaders.CLIENT_TAG_EXTRACTOR);
    }
  }

  public void observeInboundTrailers(long bytesRead) {
    super.observeInboundTrailers(bytesRead);
    if (clientMetrics != null) {
      clientMetrics.responseEnd(metric, bytesRead);
    }
    VertxTracer tracer = context.tracer();
    if (tracer != null && trace != null) {
      tracer.receiveResponse(context, inboundHeaders, trace, null, HttpResponseHeaders.CLIENT_TAG_EXTRACTOR);
    }
  }

  public void observeReset() {
    if (clientMetrics != null) {
      clientMetrics.requestReset(metric);
    }
    VertxTracer tracer = context.tracer();
    if (tracer != null && trace != null) {
      tracer.receiveResponse(context, inboundHeaders, trace, HttpUtils.STREAM_CLOSED_EXCEPTION, HttpResponseHeaders.CLIENT_TAG_EXTRACTOR);
    }
  }

  public void observeInboundHeaders(HttpHeaders headers) {
    inboundHeaders = (HttpResponseHeaders) headers;
    if (clientMetrics != null) {
      clientMetrics.responseBegin(metric, observableResponse(inboundHeaders, remoteAddress));
    }
  }

  public void observeOutboundTrailers(long bytesWritten) {
    super.observeOutboundTrailers(bytesWritten);
    if (clientMetrics != null) {
      clientMetrics.requestEnd(metric, bytesWritten);
    }
  }
}
