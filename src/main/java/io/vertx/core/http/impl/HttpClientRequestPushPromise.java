/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http.impl;

import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Stream;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpMethod;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class HttpClientRequestPushPromise extends HttpClientRequestBase {

  private final VertxHttp2ClientHandler handler;
  private final VertxHttp2ClientHandler.Http2ClientStream clientStream;
  private final HttpMethod method;
  private final String uri;
  private final String host;
  private final MultiMap headers;
  private Handler<HttpClientResponse> respHandler;

  public HttpClientRequestPushPromise(
      VertxHttp2ClientHandler handler,
      Http2Stream clientStream,
      HttpClientImpl client,
      HttpMethod method,
      String uri,
      String host,
      MultiMap headers) throws Http2Exception {
    super(client);
    this.handler = handler;
    this.clientStream = new VertxHttp2ClientHandler.Http2ClientStream(handler, this, clientStream);
    this.method = method;
    this.uri = uri;
    this.host = host;
    this.headers = headers;
  }

  VertxHttp2ClientHandler.Http2ClientStream getStream() {
    return clientStream;
  }

  @Override
  protected Object getLock() {
    return this; //
  }

  @Override
  protected void doHandleResponse(HttpClientResponseImpl resp) {
    synchronized (getLock()) {
      if (respHandler != null) {
        respHandler.handle(resp);
      }
    }
  }

  @Override
  protected void checkComplete() {
  }

  @Override
  public HttpClientRequest handler(Handler<HttpClientResponse> handler) {
    synchronized (getLock()) {
      respHandler = handler;
      return this;
    }
  }

  @Override
  public HttpConnection connection() {
    return handler;
  }

  @Override
  public void reset(long code) {
    clientStream.reset(code);
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
  public HttpClientRequest pause() {
    throw new IllegalStateException();
  }

  @Override
  public HttpClientRequest resume() {
    throw new IllegalStateException();
  }

  @Override
  public HttpClientRequest endHandler(Handler<Void> endHandler) {
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
  public HttpClientRequest pushPromiseHandler(Handler<HttpClientRequest> handler) {
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
}
