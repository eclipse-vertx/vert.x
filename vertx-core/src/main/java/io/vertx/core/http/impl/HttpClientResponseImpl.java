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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.headers.HeadersAdaptor;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.ConnectionBase;

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

  private HttpEventHandler eventHandler;
  private Handler<HttpFrame> customFrameHandler;
  private Handler<StreamPriorityBase> priorityHandler;

  // Cache these for performance
  private MultiMap headers;
  private MultiMap trailers;
  private List<String> cookies;
  private NetSocket netSocket;

  HttpClientResponseImpl(HttpClientRequestBase request, HttpVersion version, HttpClientStream stream, int statusCode, String statusMessage, MultiMap headers) {
    this.version = version;
    this.statusCode = statusCode;
    this.statusMessage = statusMessage;
    this.request = request;
    this.stream = stream;
    this.conn = stream.connection();
    this.headers = headers;
  }

  private HttpEventHandler eventHandler(boolean create) {
    if (eventHandler == null && create) {
      eventHandler = new HttpEventHandler(request.context);
    }
    return eventHandler;
  }

  @Override
  public HttpClientRequestBase request() {
    return request;
  }

  @Override
  public NetSocket netSocket() {
    if (netSocket == null) {
      netSocket = HttpNetSocket.netSocket((ConnectionBase) conn, request.context, this, stream);
    }
    return netSocket;
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
      HttpEventHandler eventHandler = eventHandler(handler != null);
      if (eventHandler != null) {
        eventHandler.chunkHandler(handler);
      }
      return this;
    }
  }

  @Override
  public HttpClientResponse endHandler(Handler<Void> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkEnded();
      }
      HttpEventHandler eventHandler = eventHandler(handler != null);
      if (eventHandler != null) {
        eventHandler.endHandler(handler);
      }
      return this;
    }
  }

  @Override
  public HttpClientResponse exceptionHandler(Handler<Throwable> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkEnded();
      }
      HttpEventHandler eventHandler = eventHandler(handler != null);
      if (eventHandler != null) {
        eventHandler.exceptionHandler(handler);
      }
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
      if (handler != null) {
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
    HttpEventHandler handler;
    synchronized (conn) {
      handler = eventHandler;
    }
    if (handler != null) {
      handler.handleChunk(data);
    }
  }

  void handleEnd(MultiMap trailers) {
    HttpEventHandler handler;
    synchronized (conn) {
      this.trailers = trailers;
      handler = eventHandler;
    }
    if (handler != null) {
      handler.handleEnd();
    }
  }

  void handleException(Throwable e) {
    HttpEventHandler handler;
    synchronized (conn) {
      if (trailers != null) {
        return;
      }
      handler = eventHandler;
    }
    if (handler != null) {
      handler.handleException(e);
    } else {
      log.error(e.getMessage(), e);
    }
  }

  @Override
  public Future<Buffer> body() {
    return eventHandler(true).body();
  }

  @Override
  public synchronized Future<Void> end() {
    checkEnded();
    return eventHandler(true).end();
  }

  @Override
  public HttpClientResponse streamPriorityHandler(Handler<StreamPriorityBase> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkEnded();
      }
      priorityHandler = handler;
    }
    return this;
  }

  void handlePriorityChange(StreamPriorityBase streamPriority) {
    Handler<StreamPriorityBase> handler;
    synchronized (conn) {
      handler = priorityHandler;
    }
    if (handler != null) {
      handler.handle(streamPriority);
    }
  }
}
