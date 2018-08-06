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
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.streams.ReadStream;

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

  @Override
  public HttpClientResponse handler(Handler<Buffer> dataHandler) {
    synchronized (conn) {
      this.dataHandler = dataHandler;
      return this;
    }
  }

  @Override
  public HttpClientResponse endHandler(Handler<Void> endHandler) {
    synchronized (conn) {
      this.endHandler = endHandler;
      return this;
    }
  }

  @Override
  public HttpClientResponse exceptionHandler(Handler<Throwable> exceptionHandler) {
    synchronized (conn) {
      this.exceptionHandler = exceptionHandler;
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
    stream.doResume();
    return this;
  }

  @Override
  public HttpClientResponse fetch(long amount) {
    stream.doFetch(amount);
    return this;
  }

  @Override
  public HttpClientResponse bodyHandler(final Handler<Buffer> bodyHandler) {
    BodyHandler handler = new BodyHandler();
    handler(handler);
    endHandler(v -> handler.notifyHandler(bodyHandler));
    return this;
  }

  @Override
  public HttpClientResponse customFrameHandler(Handler<HttpFrame> handler) {
    synchronized (conn) {
      customFrameHandler = handler;
      return this;
    }
  }

  void handleUnknownFrame(HttpFrame frame) {
    synchronized (conn) {
      if (customFrameHandler != null) {
        try {
          customFrameHandler.handle(frame);
        } catch (Throwable t) {
          handleException(t);
        }
      }
    }
  }

  void handleChunk(Buffer data) {
    synchronized (conn) {
      request.dataReceived();
      bytesRead += data.length();
      if (dataHandler != null) {
        try {
          dataHandler.handle(data);
        } catch (Throwable t) {
          handleException(t);
        }
      }
    }
  }

  void handleEnd(MultiMap trailers) {
    synchronized (conn) {
      stream.reportBytesRead(bytesRead);
      bytesRead = 0;
      this.trailers = trailers;
      if (endHandler != null) {
        try {
          endHandler.handle(null);
        } catch (Throwable t) {
          handleException(t);
        }
      }
    }
  }

  void handleException(Throwable e) {
    synchronized (conn) {
      if (exceptionHandler != null) {
        exceptionHandler.handle(e);
      } else {
        log.error(e);
      }
    }
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
}
