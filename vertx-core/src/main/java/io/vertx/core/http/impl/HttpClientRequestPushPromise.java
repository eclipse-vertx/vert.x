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
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;

import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class HttpClientRequestPushPromise extends HttpClientRequestBase {

  private final HttpClientStream stream;
  private final MultiMap headers;

  public HttpClientRequestPushPromise(
    HttpConnection connection,
    HttpClientStream stream,
    HttpMethod method,
    String uri,
    MultiMap headers) {
    super(connection, stream, stream.connection().getContext().promise(), method, uri);
    this.stream = stream;
    this.headers = headers;
  }

  @Override
  public HttpVersion version() {
    return stream.version();
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
  public boolean isChunked() {
    return false;
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
  public boolean isFollowRedirects() {
    return false;
  }

  @Override
  public HttpClientRequest setMaxRedirects(int maxRedirects) {
    throw new IllegalStateException();
  }

  @Override
  public int getMaxRedirects() {
    return 0;
  }

  @Override
  public int numberOfRedirections() {
    return 0;
  }

  @Override
  public HttpClientRequest redirectHandler(@Nullable Function<HttpClientResponse, Future<HttpClientRequest>> handler) {
    throw new IllegalStateException();
  }

  @Override
  public HttpClientRequest setChunked(boolean chunked) {
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
  public HttpClientRequest traceOperation(String op) {
    throw new IllegalStateException();
  }

  @Override
  public String traceOperation() {
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
  public HttpClientRequest continueHandler(@Nullable Handler<Void> handler) {
    throw new IllegalStateException();
  }

  @Override
  public HttpClientRequest earlyHintsHandler(@Nullable Handler<MultiMap> handler) {
    throw new IllegalStateException();
  }

  @Override
  public Future<Void> sendHead() {
    throw new IllegalStateException();
  }

  @Override
  public Future<HttpClientResponse> connect() {
    throw new IllegalStateException();
  }

  @Override
  public Future<Void> end(String chunk) {
    throw new IllegalStateException();
  }

  @Override
  public Future<Void> end(String chunk, String enc) {
    throw new IllegalStateException();
  }

  @Override
  public Future<Void> end(Buffer chunk) {
    throw new IllegalStateException();
  }

  @Override
  public Future<Void> end() {
    throw new IllegalStateException();
  }

  @Override
  public boolean writeQueueFull() {
    throw new IllegalStateException();
  }

  @Override
  public StreamPriorityBase getStreamPriority() {
    return stream.priority();
  }

  @Override
  public Future<Void> writeCustomFrame(int type, int flags, Buffer payload) {
    throw new UnsupportedOperationException("Cannot write frame with HTTP/1.x ");
  }
}
