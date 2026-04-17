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

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.headers.HeadersAdaptor;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.streams.WriteStream;

import java.util.ArrayList;
import java.util.List;

/**
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
  private Handler<StreamPriority> priorityHandler;

  // Cache these for performance
  private final MultiMap headers;
  private MultiMap trailers;
  private List<String> cookies;
  private NetSocket netSocket;

  /**
   * This {@code ended} field is used internally to track whether the response has ended.
   * The {@link #completion} promise is used to expose the response result, completed as failed
   * or succeeded. The promise's {@link Future} is exposed to external users via {@link #end()}.
   *
   * <p>The {@code ended} / {@link #completion} pair decouples the "response ended" state handling
   * from the potentially expensive work that might be incurred by handlers registered on the promise's
   * {@link Future}.
   *
   * <p>The pattern for both fields in {@link #handleException(Throwable)} and {@link #handleTrailers(MultiMap)}
   * is:
   * <ol>
   *   <li>Acquire lock ({@code synchronized (conn)}).
   *   <li>Check {@code ended}, if {@code ended != null} return immediately.
   *   <li>Set {@code ended}.
   *   <li>Release lock.
   *   <li>Complete the {@link #completion} promise.
   * </ol>
   *
   * <p>Possible states of {@code ended}:
   * <ul>
   *   <li>{@code null} - the response has not ended yet</li>
   *   <li>{@link #ENDED_SENTINEL ENDED_SENTINEL} - the response has ended successfully</li>
   *   <li>any other {@link Throwable} - the response has ended with an exception</li>
   * </ul>
   *
   * <p>All accesses to this field must be guarded by {@code conn}.
   */
  private Throwable ended;
  private static final Throwable ENDED_SENTINEL = new Throwable();
  private final Promise<Void> completion;

  HttpClientResponseImpl(HttpClientRequestBase request, HttpVersion version, HttpClientStream stream, int statusCode, String statusMessage, MultiMap headers) {
    this.version = version;
    this.statusCode = statusCode;
    this.statusMessage = statusMessage;
    this.request = request;
    this.stream = stream;
    this.conn = stream.connection();
    this.completion = request.context.promise();
    this.headers = headers;
  }

  // Must be guarded by a `synchronized (conn)` block.
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
      netSocket = HttpNetSocket.netSocket(stream, request.context, this, new WriteStream<>() {
        @Override
        public WriteStream<Buffer> exceptionHandler(@Nullable Handler<Throwable> handler) {
          stream.exceptionHandler(handler);
          return this;
        }
        @Override
        public Future<Void> write(Buffer data) {
          return stream.writeChunk(data, false);
        }
        @Override
        public Future<Void> end(Buffer data) {
          return stream.writeChunk(data, true);
        }
        @Override
        public Future<Void> end() {
          return stream.writeChunk(BufferInternal.buffer(Unpooled.EMPTY_BUFFER), true);
        }
        @Override
        public WriteStream<Buffer> setWriteQueueMaxSize(int maxSize) {
          stream.setWriteQueueMaxSize(maxSize);
          return this;
        }
        @Override
        public boolean writeQueueFull() {
          return !stream.isWritable();
        }
        @Override
        public WriteStream<Buffer> drainHandler(@Nullable Handler<Void> handler) {
          stream.drainHandler(handler);
          return this;
        }
      });
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
    MultiMap trailers;
    synchronized (conn) {
      trailers = this.trailers;
    }
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

  /** Must be called within a {@code synchronized (conn)} block. */
  private void checkEnded() {
    if (ended != null) {
      throw new IllegalStateException("Response already ended");
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
    stream.pause();
    return this;
  }

  @Override
  public HttpClientResponse resume() {
    return fetch(Long.MAX_VALUE);
  }

  @Override
  public HttpClientResponse fetch(long amount) {
    stream.fetch(amount);
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
    HttpEventHandler handler;
    synchronized (conn) {
      handler = eventHandler;
    }
    if (handler != null) {
      handler.handleChunk(data);
    }
  }

  void handleTrailers(MultiMap trailers) {
    HttpEventHandler handler;
    Throwable wasEnded;
    // This synchronized block is used to guarantee that a handler's `handleEnd()` is
    // called only once and that setting/updating `trailers` does not race with
    // `trailers()`, where the `trailers` field can escape.
    synchronized (conn) {
      wasEnded = this.ended;
      if (wasEnded != null) {
        return;
      }
      if (this.trailers == null) {
        this.trailers = trailers;
      } else if (this.trailers != trailers) {
        this.trailers.setAll(trailers);
      }
      this.ended = ENDED_SENTINEL;
      handler = eventHandler;
    }
    completion.tryComplete();
    if (handler != null) {
      handler.handleEnd();
    }
  }

  void handleException(Throwable e) {
    HttpEventHandler handler;
    Throwable wasEnded;
    synchronized (conn) {
      // Only report the first exception, and generally only report when the
      // response has not yet ended.
      wasEnded = this.ended;
      if (wasEnded != null) {
        return;
      }
      this.ended = e;
      handler = eventHandler;
    }
    completion.tryFail(e);
    if (handler != null) {
      handler.handleException(e);
    } else {
      log.error(e.getMessage(), e);
    }
  }

  @Override
  public Future<Buffer> body() {
    HttpEventHandler eventHandler;
    Future<Buffer> bodyFuture;
    Throwable wasEnded;
    synchronized (conn) {
      eventHandler = eventHandler(true);
      bodyFuture = eventHandler.body();
      wasEnded = ended;
    }
    if (wasEnded == ENDED_SENTINEL) {
      eventHandler.handleEnd();
    } else if (wasEnded != null) {
      eventHandler.handleException(wasEnded);
    }
    return bodyFuture;
  }

  @Override
  public Future<Void> end() {
    return completion.future();
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
