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
import io.vertx.core.http.HttpClientRequestBuilder;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpClientResponseBuilder;
import io.vertx.core.http.HttpResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.streams.ReadStream;

import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class HttpClientResponseBuilderImpl<T> implements HttpClientResponseBuilder<T> {

  private final HttpClientRequestBuilder requestBuilder;
  private final Function<Buffer, T> bodyUnmarshaller;

  HttpClientResponseBuilderImpl(HttpClientRequestBuilder requestBuilder, Function<Buffer, T> bodyUnmarshaller) {
    this.requestBuilder = requestBuilder;
    this.bodyUnmarshaller = bodyUnmarshaller;
  }

  private Handler<AsyncResult<HttpClientResponse>> createClientResponseHandler(Handler<AsyncResult<HttpResponse<T>>> handler) {
    return ar -> {
      if (ar.succeeded()) {
        HttpClientResponse resp = ar.result();
        resp.bodyHandler(buff -> {
          T body;
          try {
            body = bodyUnmarshaller.apply(buff);
          } catch (Throwable err) {
            handler.handle(Future.failedFuture(err));
            return;
          }
          handler.handle(Future.succeededFuture(new HttpResponse<T>() {
            @Override
            public HttpVersion version() {
              return resp.version();
            }
            @Override
            public int statusCode() {
              return resp.statusCode();
            }
            @Override
            public String statusMessage() {
              return resp.statusMessage();
            }
            @Override
            public MultiMap headers() {
              return resp.headers();
            }
            @Override
            public T body() {
              return body;
            }
          }));
        });
      } else {
        handler.handle(Future.failedFuture(ar.cause()));
      }
    };
  }

  @Override
  public void send(ReadStream<Buffer> stream, Handler<AsyncResult<HttpResponse<T>>> handler) {
    requestBuilder.send(stream, createClientResponseHandler(handler));
  }

  @Override
  public void send(Handler<AsyncResult<HttpResponse<T>>> handler) {
    requestBuilder.send(createClientResponseHandler(handler));
  }
}
