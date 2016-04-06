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

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpVersion;
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

  private final HttpVersion version;
  private final int statusCode;
  private final String statusMessage;
  private final HttpClientRequestBase request;
  private final HttpClientConnection conn;
  private final HttpClientStream stream;

  private Handler<Buffer> dataHandler;
  private Handler<HttpFrame> unknownFrameHandler;
  private Handler<Void> endHandler;
  private Handler<Throwable> exceptionHandler;
  private boolean hasPausedEnd;
  private boolean paused;
  private Buffer pausedLastChunk;
  private MultiMap pausedTrailers;
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

  HttpClientRequestBase request() {
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
    synchronized (conn) {
      if (!paused) {
        paused = true;
        stream.doPause();
      }
      return this;
    }
  }

  @Override
  public HttpClientResponse resume() {
    synchronized (conn) {
      if (paused) {
        paused = false;
        doResume();
        stream.doResume();
      }
      return this;
    }
  }

  @Override
  public HttpClientResponse bodyHandler(final Handler<Buffer> bodyHandler) {
    BodyHandler handler = new BodyHandler();
    handler(handler);
    endHandler(v -> handler.notifyHandler(bodyHandler));
    return this;
  }

  @Override
  public HttpClientResponse unknownFrameHandler(Handler<HttpFrame> handler) {
    synchronized (conn) {
      unknownFrameHandler = handler;
      return this;
    }
  }

  private void doResume() {
    if (hasPausedEnd) {
      final Buffer theChunk = pausedLastChunk;
      final MultiMap theTrailer = pausedTrailers;
      stream.getContext().runOnContext(v -> handleEnd(theChunk, theTrailer));
      hasPausedEnd = false;
      pausedTrailers = null;
      pausedLastChunk = null;
    }
  }

  void handleUnknowFrame(HttpFrame frame) {
    synchronized (conn) {
      if (unknownFrameHandler != null) {
        try {
          unknownFrameHandler.handle(frame);
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

  void handleEnd(Buffer lastChunk, MultiMap trailers) {
    synchronized (conn) {
      conn.reportBytesRead(bytesRead);
      bytesRead = 0;
      if (paused) {
        pausedLastChunk = lastChunk;
        hasPausedEnd = true;
        pausedTrailers = trailers;
      } else {
        if (lastChunk != null) {
          handleChunk(lastChunk);
        }
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
  }

  void handleException(Throwable e) {
    synchronized (conn) {
      if (exceptionHandler != null) {
        exceptionHandler.handle(e);
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
