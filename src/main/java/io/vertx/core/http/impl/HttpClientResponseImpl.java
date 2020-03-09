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

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.headers.HeadersAdaptor;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is optimised for performance when used on the same event loop that is was passed to the handler with.
 * However it can be used safely from other threads.
 *
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpClientResponseImpl implements HttpClientResponse  {

  private static final Logger log = LoggerFactory.getLogger(HttpClientResponseImpl.class);

  private final HttpVersion version;
  private final int statusCode;
  private final String statusMessage;
  private final HttpClientRequestBase request;
  private final HttpConnection conn;
  private final HttpClientStream stream;

  private Handler<Buffer> dataHandler;
  private Handler<HttpFrame> customFrameHandler;
  private Handler<Void> endHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<StreamPriority> priorityHandler;

  // Cache these for performance
  private MultiMap headers;
  private MultiMap trailers;
  private List<String> cookies;

  HttpClientResponseImpl(HttpClientRequestBase request, HttpVersion version, HttpClientStream stream, int statusCode, String statusMessage, MultiMap headers) {
    this.version = version;
    this.statusCode = statusCode;
    this.statusMessage = statusMessage;
    this.request = request;
    this.stream = stream;
    this.conn = stream.connection();
    this.headers = headers;
  }

  @Override
  public HttpClientRequestBase request() {
    return request;
  }

  @Override
  public HttpVersion version() {
    return version;
  }

  @Override
  public int statusCode() {
    return statusCode;
  }

  @Override
  public String statusMessage() {
    return statusMessage;
  }

  @Override
  public MultiMap headers() {
    return headers;
  }

  @Override
  public String getHeader(String headerName) {
    return headers.get(headerName);
  }

  @Override
  public String getHeader(CharSequence headerName) {
    return headers.get(headerName);
  }

  @Override
  public MultiMap trailers() {
    synchronized (conn) {
      if (trailers == null) {
        trailers = new HeadersAdaptor(new DefaultHttpHeaders());
      }
      return trailers;
    }
  }

  @Override
  public String getTrailer(String trailerName) {
    return trailers != null ? trailers.get(trailerName) : null;
  }

  @Override
  public List<String> cookies() {
    synchronized (conn) {
      if (cookies == null) {
        cookies = new ArrayList<>();
        cookies.addAll(headers().getAll(HttpHeaders.SET_COOKIE));
        if (trailers != null) {
          cookies.addAll(trailers.getAll(HttpHeaders.SET_COOKIE));
        }
      }
      return cookies;
    }
  }

  private void checkEnded() {
    if (trailers != null) {
      throw new IllegalStateException();
    }
  }

  @Override
  public HttpClientResponse handler(Handler<Buffer> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkEnded();
      }
      dataHandler = handler;
      return this;
    }
  }

  @Override
  public HttpClientResponse endHandler(Handler<Void> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkEnded();
      }
      endHandler = handler;
      return this;
    }
  }

  @Override
  public HttpClientResponse exceptionHandler(Handler<Throwable> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkEnded();
      }
      exceptionHandler = handler;
      return this;
    }
  }

  @Override
  public HttpClientResponse pause() {
    stream.doPause();
    return this;
  }

  @Override
  public HttpClientResponse resume() {
    return fetch(Long.MAX_VALUE);
  }

  @Override
  public HttpClientResponse fetch(long amount) {
    stream.doFetch(amount);
    return this;
  }

  @Override
  public HttpClientResponse customFrameHandler(Handler<HttpFrame> handler) {
    synchronized (conn) {
      if (endHandler != null) {
        checkEnded();
      }
      customFrameHandler = handler;
      return this;
    }
  }

  void handleUnknownFrame(HttpFrame frame) {
    synchronized (conn) {
      if (customFrameHandler != null) {
        customFrameHandler.handle(frame);
      }
    }
  }

  void handleChunk(Buffer data) {
    request.dataReceived();
    Handler<Buffer> handler;
    synchronized (conn) {
      handler = dataHandler;
      if (handler == null) {
        return;
      }
    }
    request.context.emit(data, handler);
  }

  void handleEnd(MultiMap trailers) {
    Handler<Void> handler;
    synchronized (conn) {
      this.trailers = trailers;
      handler = endHandler;
      endHandler = null;
      if (handler == null) {
        return;
      }
    }
    request.context.emit(handler);
  }

  void handleException(Throwable e) {
    Handler<Throwable> handler;
    synchronized (conn) {
      if (trailers != null) {
        return;
      }
      handler = exceptionHandler;
      if (handler == null) {
        handler = log::error;
      }
    }
    handler.handle(e);
  }

  @Override
  public HttpClientResponse bodyHandler(Handler<Buffer> bodyHandler) {
    BodyHandler handler = new BodyHandler();
    handler(handler);
    endHandler(handler::handleEnd);
    handler.promise.future().onComplete(ar -> {
      if (ar.succeeded()) {
        bodyHandler.handle(ar.result());
      }
    });
    return this;
  }

  @Override
  public Future<Buffer> body() {
    BodyHandler handler = new BodyHandler();
    handler(handler);
    exceptionHandler(handler::handleException);
    endHandler(handler::handleEnd);
    return handler.promise.future();
  }

  private static final class BodyHandler implements Handler<Buffer> {

    private Promise<Buffer> promise = Promise.promise();
    private Buffer body = Buffer.buffer();

    @Override
    public void handle(Buffer event) {
      body.appendBuffer(event);
    }

    void handleEnd(Void v) {
      promise.tryComplete(body);
    }

    void handleException(Throwable err) {
      promise.tryFail(err);
    }
  }

  @Override
  public HttpClientResponse streamPriorityHandler(Handler<StreamPriority> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkEnded();
      }
      priorityHandler = handler;
    }
    return this;
  }

  void handlePriorityChange(StreamPriority streamPriority) {
    Handler<StreamPriority> handler;
    synchronized (conn) {
      handler = priorityHandler;
    }
    if (handler != null) {
      handler.handle(streamPriority);
    }
  }
}
