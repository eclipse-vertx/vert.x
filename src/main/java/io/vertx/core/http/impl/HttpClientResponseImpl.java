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

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;

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
  private NetSocket netSocket;

  // Track for metrics
  private long bytesRead;

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
    return headers().get(headerName);
  }

  @Override
  public String getHeader(CharSequence headerName) {
    return headers().get(headerName);
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
  public HttpClientResponse handler(Handler<Buffer> handle) {
    synchronized (conn) {
      if (handle != null) {
        checkEnded();
      }
      dataHandler = handle;
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
  public HttpClientResponse bodyHandler(final Handler<Buffer> handler) {
    if (handler != null) {
      BodyHandler bodyHandler = new BodyHandler();
      handler(bodyHandler);
      endHandler(v -> bodyHandler.notifyHandler(handler));
    } else {
      handler(null);
      endHandler(null);
    }
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
        ContextInternal ctx = (ContextInternal) stream.getContext();
        ctx.dispatch(frame, customFrameHandler);
      }
    }
  }

  void handleChunk(Buffer data) {
    synchronized (conn) {
      request.dataReceived();
      bytesRead += data.length();
      if (dataHandler != null) {
        ((ContextInternal)stream.getContext()).dispatch(data, dataHandler);
      }
    }
  }

  void handleEnd(MultiMap trailers) {
    Handler<Void> handler;
    synchronized (conn) {
      stream.reportBytesRead(bytesRead);
      bytesRead = 0;
      this.trailers = trailers;
      handler = endHandler;
      endHandler = null;
    }
    if (handler != null) {
      try {
        stream.getContext().dispatch(null, handler);
      } catch (Throwable t) {
        handleException(t);
      }
    }
  }

  void handleException(Throwable e) {
    Handler<Throwable> handler;
    synchronized (conn) {
      handler = exceptionHandler;
      if (handler == null) {
        handler = log::error;
      }
    }
    ContextInternal ctx = (ContextInternal) stream.getContext();
    ctx.dispatch(e, handler);
  }

  @Override
  public NetSocket netSocket() {
    synchronized (conn) {
      if (netSocket == null) {
        netSocket = stream.createNetSocket();
      }
      return netSocket;
    }
  }

  private static final class BodyHandler implements Handler<Buffer> {
    private Buffer body;

    @Override
    public void handle(Buffer event) {
      body().appendBuffer(event);
    }

    private Buffer body() {
      if (body == null) {
        body = Buffer.buffer();
      }
      return body;
    }

    void notifyHandler(Handler<Buffer> bodyHandler) {
      bodyHandler.handle(body());
      // reset body so it can get GC'ed
      body = null;
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
      if ((handler = priorityHandler) == null) {
        return;
      }
    }
    stream.getContext().dispatch(streamPriority, handler);
  }
}
