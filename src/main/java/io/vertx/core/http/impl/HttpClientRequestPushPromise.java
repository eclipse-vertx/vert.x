/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;

import io.netty.handler.codec.http2.Http2Stream;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class HttpClientRequestPushPromise extends HttpClientRequestBase {

  private final Http2ClientConnection conn;
  private final Http2ClientConnection.Http2ClientStream stream;
  private final String rawMethod;
  private final MultiMap headers;
  private Handler<HttpClientResponse> respHandler;

  public HttpClientRequestPushPromise(
      Http2ClientConnection conn,
      Http2Stream stream,
      HttpClientImpl client,
      boolean ssl,
      HttpMethod method,
      String rawMethod,
      String uri,
      String host,
      int port,
      MultiMap headers) {
    super(client, ssl, method, host, port, uri);
    this.conn = conn;
    this.stream = new Http2ClientConnection.Http2ClientStream(conn, this, stream, false);
    this.rawMethod = rawMethod;
    this.headers = headers;
  }

  Http2ClientConnection.Http2ClientStream getStream() {
    return stream;
  }

  @Override
  protected void doHandleResponse(HttpClientResponseImpl resp, long timeoutMs) {
    Handler<HttpClientResponse> handler;
    synchronized (this) {
      if ((handler = respHandler) == null) {
        return;
      }
    }
    handler.handle(resp);
  }

  @Override
  protected void checkComplete() {
  }

  @Override
  public synchronized HttpClientRequest handler(Handler<HttpClientResponse> handler) {
    respHandler = handler;
    return this;
  }

  @Override
  public HttpConnection connection() {
    return conn;
  }

  @Override
  public HttpClientRequest connectionHandler(@Nullable Handler<HttpConnection> handler) {
    return this;
  }

  @Override
  public boolean reset(long code) {
    synchronized (conn) {
      stream.reset(code);
      return true;
    }
  }

  @Override
  public boolean isChunked() {
    return false;
  }

  @Override
  public HttpMethod method() {
    return method;
  }

  @Override
  public String getRawMethod() {
    return rawMethod;
  }

  @Override
  public HttpClientRequest setRawMethod(String method) {
    throw new IllegalStateException();
  }

  @Override
  public String uri() {
    return uri;
  }

  @Override
  public String getHost() {
    return host;
  }

  @Override
  public MultiMap headers() {
    return headers;
  }

  @Override
  public HttpClientRequest write(Buffer data) {
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
  public HttpClientRequest endHandler(Handler<Void> endHandler) {
    throw new IllegalStateException();
  }

  @Override
  public HttpClientRequest setFollowRedirects(boolean followRedirect) {
    throw new IllegalStateException();
  }

  @Override
  public HttpClientRequest setChunked(boolean chunked) {
    throw new IllegalStateException();
  }

  @Override
  public HttpClientRequest setHost(String host) {
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
  public HttpClientRequest write(String chunk) {
    throw new IllegalStateException();
  }

  @Override
  public HttpClientRequest write(String chunk, String enc) {
    throw new IllegalStateException();
  }

  @Override
  public HttpClientRequest continueHandler(@Nullable Handler<Void> handler) {
    throw new IllegalStateException();
  }

  @Override
  public HttpClientRequest sendHead() {
    throw new IllegalStateException();
  }

  @Override
  public HttpClientRequest sendHead(Handler<HttpVersion> completionHandler) {
    throw new IllegalStateException();
  }

  @Override
  public void end(String chunk) {
    throw new IllegalStateException();
  }

  @Override
  public void end(String chunk, String enc) {
    throw new IllegalStateException();
  }

  @Override
  public void end(Buffer chunk) {
    throw new IllegalStateException();
  }

  @Override
  public HttpClientRequest pushHandler(Handler<HttpClientRequest> handler) {
    throw new IllegalStateException();
  }

  @Override
  public void end() {
    throw new IllegalStateException();
  }

  @Override
  public boolean writeQueueFull() {
    throw new IllegalStateException();
  }

  @Override
  public HttpClientRequest writeCustomFrame(int type, int flags, Buffer payload) {
    throw new UnsupportedOperationException("Cannot write frame with HTTP/1.x ");
  }
}
