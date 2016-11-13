/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.http.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientRequestBuilder;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpClientResponseBuilder;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.Pump;
import io.vertx.core.streams.ReadStream;

import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class HttpClientRequestBuilderImpl implements HttpClientRequestBuilder {

  final HttpClient client;
  HttpMethod method;
  Integer port;
  String host;
  String requestURI;
  MultiMap headers;

  HttpClientRequestBuilderImpl(HttpClient client, HttpMethod method) {
    this.client = client;
    this.method = method;
  }

  private HttpClientRequestBuilderImpl(HttpClientRequestBuilderImpl other) {
    this.client = other.client;
    this.method = other.method;
    this.port = other.port;
    this.host = other.host;
    this.requestURI = other.requestURI;
    this.headers = other.headers != null ? new CaseInsensitiveHeaders().addAll(other.headers) : null;
  }

  @Override
  public HttpClientRequestBuilder method(HttpMethod method) {
    HttpClientRequestBuilderImpl other = new HttpClientRequestBuilderImpl(this);
    other.method = method;
    return other;
  }

  @Override
  public HttpClientRequestBuilder port(int port) {
    HttpClientRequestBuilderImpl other = new HttpClientRequestBuilderImpl(this);
    other.port = port;
    return other;
  }

  @Override
  public HttpClientRequestBuilder host(String host) {
    HttpClientRequestBuilderImpl other = new HttpClientRequestBuilderImpl(this);
    other.host = host;
    return other;
  }

  @Override
  public HttpClientRequestBuilder requestURI(String requestURI) {
    HttpClientRequestBuilderImpl other = new HttpClientRequestBuilderImpl(this);
    other.requestURI = requestURI;
    return other;
  }

  @Override
  public HttpClientRequestBuilder putHeader(String name, String value) {
    HttpClientRequestBuilderImpl other = new HttpClientRequestBuilderImpl(this);
    if (other.headers == null) {
      other.headers = new CaseInsensitiveHeaders();
    }
    other.headers.add(name, value);
    return other;
  }

  @Override
  public void send(ReadStream<Buffer> stream, Handler<AsyncResult<HttpClientResponse>> handler) {
    perform(stream, handler);
  }

  @Override
  public void send(Handler<AsyncResult<HttpClientResponse>> handler) {
    perform(null, handler);
  }

  private void perform(ReadStream<Buffer> stream, Handler<AsyncResult<HttpClientResponse>> handler) {
    Future<HttpClientResponse> fut = Future.future();
    HttpClientRequest req = client.request(method, port, host, requestURI);
    if (headers != null) {
      req.headers().addAll(headers);
    }
    req.exceptionHandler(err -> {
      if (!fut.isComplete()) {
        fut.fail(err);
      }
    });
    req.handler(resp -> {
      if (!fut.isComplete()) {
        fut.complete(resp);
      }
    });
    if (stream != null) {
      if (headers == null || !headers.contains("Content-Length")) {
        req.setChunked(true);
      }
      Pump pump = Pump.pump(stream, req);
      stream.exceptionHandler(err -> {
        req.reset();
        if (!fut.isComplete()) {
          fut.fail(err);
        }
      });
      stream.endHandler(v -> {
        pump.stop();
        req.end();
      });
      pump.start();
    } else {
      req.end();
    }
    fut.setHandler(handler);
  }

  @Override
  public HttpClientResponseBuilder<Buffer> asBuffer() {
    return new HttpClientResponseBuilderImpl<>(this, Function.identity());
  }

  @Override
  public HttpClientResponseBuilder<String> asString() {
    return new HttpClientResponseBuilderImpl<>(this, Buffer::toString);
  }

  @Override
  public HttpClientResponseBuilder<JsonObject> asJsonObject() {
    return new HttpClientResponseBuilderImpl<>(this, buff -> new JsonObject(buff.toString()));
  }

  @Override
  public <T> HttpClientResponseBuilder<T> as(Class<T> clazz) {
    throw new UnsupportedOperationException();
  }
}
