/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl.http3;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http3.*;
import io.netty.handler.stream.ChunkedInput;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.HttpRequestHead;
import io.vertx.core.http.impl.HttpResponseHead;
import io.vertx.core.http.impl.HttpServerConnection;
import io.vertx.core.http.impl.HttpServerStream;
import io.vertx.core.http.impl.headers.HttpHeaders;
import io.vertx.core.http.impl.headers.HttpRequestHeaders;
import io.vertx.core.http.impl.headers.HttpResponseHeaders;
import io.vertx.core.http.impl.observability.ServerStreamObserver;
import io.vertx.core.http.impl.observability.StreamObserver;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.quic.QuicStreamInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.spi.metrics.HttpServerMetrics;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http3ServerStream extends Http3Stream<Http3ServerStream, Http3ServerConnection> implements HttpServerStream {

  private final ServerStreamObserver observer;
  private Handler<HttpRequestHead> headHandler;
  private boolean endReceived;

  public Http3ServerStream(Http3ServerConnection connection, QuicStreamInternal stream, ContextInternal context,
                           ServerStreamObserver observer) {
    super(connection, stream, context, observer);

    this.observer = observer;
  }

  @Override
  protected HttpHeaders headOf(Http3Headers headers) {
    return new HttpRequestHeaders(headers);
  }

  @Override
  protected boolean handleHead(HttpHeaders headers) {
    HttpRequestHeaders requestHeaders = (HttpRequestHeaders) headers;
    boolean valid = super.handleHead(requestHeaders);
    if (valid) {
      HttpRequestHead head = new HttpRequestHead(
        requestHeaders.scheme(),
        requestHeaders.method(),
        requestHeaders.path(),
        requestHeaders,
        requestHeaders.authority(),
        null,
        null);
      Handler<HttpRequestHead> handler = headHandler;
      if (handler != null) {
        context.emit(head, handler);
      }
    }
    return valid;
  }

  @Override
  protected void handleEnd() {
    endReceived = true;
    super.handleEnd();
  }

  @Override
  protected void handleReset(long code) {
    super.handleReset(code);
    if (!endReceived) {
      stream.reset(Http3ErrorCode.H3_REQUEST_INCOMPLETE.code());
    }
  }

  @Override
  public void routed(String route) {
    if (observer != null) {
      observer.observeRoute(route);
    }
  }

  @Override
  public HttpServerConnection connection() {
    return connection;
  }

  @Override
  public Future<Void> writeHead(HttpResponseHead head, Buffer chunk, boolean end) {
    HttpResponseHeaders headers = (HttpResponseHeaders) head.headers;
    headers.status(head.statusCode);
    headers.prepare();
    return writeHeaders(headers, chunk, end);
  }

  @Override
  public Future<Void> writeHeaders(MultiMap headers, boolean end) {
    return writeHeaders((HttpHeaders) headers, null, end);
  }

  @Override
  public Future<HttpServerStream> sendPush(HostAndPort authority, HttpMethod method, MultiMap headers, String path, StreamPriority priority) {
    return null;
  }

  @Override
  public HttpServerStream headHandler(Handler<HttpRequestHead> handler) {
    this.headHandler = handler;
    return this;
  }

  @Override
  public HttpServerStream priorityChangeHandler(Handler<StreamPriority> handler) {
    return this;
  }

  @Override
  public void sendFile(ChunkedInput<ByteBuf> file, Promise<Void> promise) {

  }

  @Override
  public HttpServerStream updatePriority(StreamPriority streamPriority) {
    return null;
  }

  @Override
  public Future<Void> writeFrame(int type, int flags, Buffer payload) {
    return null;
  }

  @Override
  public StreamPriority priority() {
    return null;
  }
}
