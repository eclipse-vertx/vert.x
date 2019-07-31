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
package io.vertx.core.http.impl;

import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.metrics.Metrics;

import static io.vertx.core.spi.metrics.Metrics.METRICS_ENABLED;

abstract class Http2ServerStream extends VertxHttp2Stream<Http2ServerConnection> {

  protected final Http2Headers headers;
  protected final String rawMethod;
  protected final HttpMethod method;
  protected final String uri;
  protected final String contentEncoding;
  protected final String host;
  protected final Http2ServerResponseImpl response;
  private Object metric;

  Http2ServerStream(Http2ServerConnection conn,
                    ContextInternal context,
                    Http2Stream stream,
                    String contentEncoding,
                    HttpMethod method,
                    String uri,
                    boolean writable) {
    super(conn, context, stream, writable);

    this.headers = null;
    this.method = method;
    this.rawMethod = method.name();
    this.contentEncoding = contentEncoding;
    this.uri = uri;
    this.host = null;
    this.response = new Http2ServerResponseImpl(conn, this, method, true, contentEncoding, null);
  }

  Http2ServerStream(Http2ServerConnection conn, ContextInternal context, Http2Stream stream, Http2Headers headers, String contentEncoding, String serverOrigin, boolean writable) {
    super(conn, context, stream, writable);

    String host = headers.get(":authority") != null ? headers.get(":authority").toString() : null;
    if (host == null) {
      int idx = serverOrigin.indexOf("://");
      host = serverOrigin.substring(idx + 3);
    }

    this.headers = headers;
    this.host = host;
    this.contentEncoding = contentEncoding;
    this.uri = headers.get(":path") != null ? headers.get(":path").toString() : null;
    this.rawMethod = headers.get(":method") != null ? headers.get(":method").toString() : null;
    this.method = HttpUtils.toVertxMethod(rawMethod);
    this.response = new Http2ServerResponseImpl(conn, this, method, false, contentEncoding, host);
  }

  void registerMetrics() {
    if (METRICS_ENABLED) {
      HttpServerMetrics metrics = conn.metrics();
      if (metrics != null) {
        if (response.isPush()) {
          metric = metrics.responsePushed(conn.metric(), method(), uri, response);
        } else {
          metric = metrics.requestBegin(conn.metric(), (HttpServerRequest) this);
        }
      }
    }
  }

  void writeHead(Http2Headers headers, boolean end, Handler<AsyncResult<Void>> handler) {
    if (Metrics.METRICS_ENABLED && metric != null) {
      conn.metrics().responseBegin(metric, response);
    }
    writeHeaders(headers, end, handler);
  }

  @Override
  void handleInterestedOpsChanged() {
    if (response != null) {
      response.writabilityChanged();
    }
  }

  public HttpMethod method() {
    return method;
  }

  public String rawMethod() {
    return rawMethod;
  }

  @Override
  void handleClose() {
    super.handleClose();
    if (METRICS_ENABLED) {
      HttpServerMetrics metrics = conn.metrics();
      if (metrics != null) {
        // Null in case of push response : handle this case
        boolean failed = !response.ended();
        if (failed) {
          metrics.requestReset(metric);
        } else {
          metrics.responseEnd(metric, response);
        }
      }
    }
  }
}
