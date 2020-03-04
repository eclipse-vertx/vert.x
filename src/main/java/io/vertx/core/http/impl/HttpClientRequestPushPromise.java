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

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.SocketAddress;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class HttpClientRequestPushPromise extends HttpClientRequestBase {

  private final Http2ClientConnection conn;
  private final Http2ClientConnection.StreamImpl stream;
  private final MultiMap headers;

  public HttpClientRequestPushPromise(
    Http2ClientConnection conn,
    HttpClientImpl client,
    boolean ssl,
    HttpMethod method,
    String uri,
    String host,
    int port,
    MultiMap headers) {
    super(client, conn.getContext().promise(), ssl, method, SocketAddress.inetSocketAddress(port, host), host, port, uri);
    this.conn = conn;
    this.stream = new Http2ClientConnection.StreamImpl(conn, conn.getContext(), this, null);
    this.headers = headers;
  }

  Http2ClientConnection.StreamImpl getStream() {
    return stream;
  }

  @Override
  void handleResponse(Promise<HttpClientResponse> promise, HttpClientResponse resp, long timeoutMs) {
    promise.complete(resp);
  }

  @Override
  public HttpClientRequest exceptionHandler(Handler<Throwable> handler) {
    return this;
  }

  @Override
  public HttpConnection connection() {
    return conn;
  }

  @Override
  boolean reset(Throwable cause) {
    stream.reset(cause);
    return true;
  }

  @Override
  public boolean isChunked() {
    return false;
  }


  @Override
  public String getAuthority() {
    return server.host();
  }

  @Override
  public MultiMap headers() {
    return headers;
  }

  @Override
  public Future<Void> write(Buffer data) {
    throw new IllegalStateException();
  }

  @Override
  public HttpClientRequest setWriteQueueMaxSize(int maxSize) {
    throw new IllegalStateException();
  }

  @Override
  public HttpClientRequest drainHandler(Handler<Void> handler) {
    throw new IllegalStateException();
  }

  @Override
  public HttpClientRequest setFollowRedirects(boolean followRedirect) {
    throw new IllegalStateException();
  }

  @Override
  public HttpClientRequest setMaxRedirects(int maxRedirects) {
    throw new IllegalStateException();
  }

  @Override
  public HttpClientRequest setChunked(boolean chunked) {
    throw new IllegalStateException();
  }

  @Override
  public HttpClientRequest setAuthority(String authority) {
    throw new IllegalStateException();
  }

  @Override
  public HttpClientRequest putHeader(String name, String value) {
    throw new IllegalStateException();
  }

  @Override
  public HttpClientRequest putHeader(CharSequence name, CharSequence value) {
    throw new IllegalStateException();
  }

  @Override
  public HttpClientRequest putHeader(String name, Iterable<String> values) {
    throw new IllegalStateException();
  }

  @Override
  public HttpClientRequest putHeader(CharSequence name, Iterable<CharSequence> values) {
    throw new IllegalStateException();
  }

  @Override
  public Future<Void> write(String chunk) {
    throw new IllegalStateException();
  }

  @Override
  public Future<Void> write(String chunk, String enc) {
    throw new IllegalStateException();
  }

  @Override
  public void write(Buffer data, Handler<AsyncResult<Void>> handler) {
    throw new IllegalStateException();
  }

  @Override
  public void write(String chunk, Handler<AsyncResult<Void>> handler) {
    throw new IllegalStateException();
  }

  @Override
  public void write(String chunk, String enc, Handler<AsyncResult<Void>> handler) {
    throw new IllegalStateException();
  }

  @Override
  public HttpClientRequest continueHandler(@Nullable Handler<Void> handler) {
    throw new IllegalStateException();
  }

  @Override
  public Future<HttpVersion> sendHead() {
    throw new IllegalStateException();
  }

  @Override
  public HttpClientRequest sendHead(Handler<AsyncResult<HttpVersion>> completionHandler) {
    throw new IllegalStateException();
  }

  @Override
  public Future<Void> end(String chunk) {
    throw new IllegalStateException();
  }

  @Override
  public void end(String chunk, Handler<AsyncResult<Void>> handler) {
    throw new IllegalStateException();
  }

  @Override
  public Future<Void> end(String chunk, String enc) {
    throw new IllegalStateException();
  }

  @Override
  public void end(String chunk, String enc, Handler<AsyncResult<Void>> handler) {
    throw new IllegalStateException();
  }

  @Override
  public Future<Void> end(Buffer chunk) {
    throw new IllegalStateException();
  }

  @Override
  public void end(Buffer chunk, Handler<AsyncResult<Void>> handler) {
    throw new IllegalStateException();
  }

  @Override
  public HttpClientRequest pushHandler(Handler<HttpClientRequest> handler) {
    throw new IllegalStateException();
  }

  @Override
  public Future<Void> end() {
    throw new IllegalStateException();
  }

  @Override
  public void end(Handler<AsyncResult<Void>> handler) {
    throw new IllegalStateException();
  }

  @Override
  public boolean writeQueueFull() {
    throw new IllegalStateException();
  }

  @Override
  public StreamPriority getStreamPriority() {
    return stream.priority();
  }

  @Override
  public HttpClientRequest writeCustomFrame(int type, int flags, Buffer payload) {
    throw new UnsupportedOperationException("Cannot write frame with HTTP/1.x ");
  }
}
