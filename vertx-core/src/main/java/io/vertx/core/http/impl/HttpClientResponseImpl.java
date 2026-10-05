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

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.headers.HeadersAdaptor;
import io.vertx.core.impl.buffer.VertxByteBufAllocator;
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
public class HttpClientResponseImpl implements HttpClientResponse, ContentDecoder  {

  private static final Throwable ENDED_SENTINEL = new Throwable();

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
  private FlowController flowController;

  // Cache these for performance
  private final MultiMap headers;
  private boolean trailersReceived;
  private MultiMap trailers;
  private List<String> cookies;
  private NetSocket netSocket;

  private Throwable ended;
  private final Promise<Void> endFuture;

  HttpClientResponseImpl(HttpClientRequestBase request, HttpVersion version, HttpClientStream stream, int statusCode, String statusMessage, MultiMap headers) {
    this.version = version;
    this.statusCode = statusCode;
    this.statusMessage = statusMessage;
    this.request = request;
    this.stream = stream;
    this.conn = stream.connection();
    this.endFuture = request.context.promise();
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
      netSocket = HttpNetSocket.netSocket(stream, request.context, this, new WriteStream<>() {
        @Override
        public WriteStream<Buffer> exceptionHandler(@Nullable Handler<Throwable> handler) {
          stream.exceptionHandler(handler);
          return this;
        }
        @Override
        public Future<Void> write(Buffer data) {
          return stream.write(data);
        }
        @Override
        public Future<Void> end(Buffer data) {
          return stream.end(data);
        }
        @Override
        public Future<Void> end() {
          return stream.end();
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
    flowController.pause();
    return this;
  }

  @Override
  public HttpClientResponse resume() {
    return fetch(Long.MAX_VALUE);
  }

  @Override
  public HttpClientResponse fetch(long amount) {
    flowController.fetch(amount);
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

  void handleTrailers(MultiMap map) {
    synchronized (conn) {
      MultiMap t = trailers;
      if (t != null) {
        t.setAll(map);
      } else {
        trailers = map;
      }
      trailersReceived = true;
    }
  }

  public void handleEnd() {
    HttpEventHandler handler;
    synchronized (conn) {
      ended = ENDED_SENTINEL;
      handler = eventHandler;
    }
    endFuture.tryComplete();
    if (handler != null) {
      handler.handleEnd();
    }
  }

  void handleException(Throwable err) {
    HttpEventHandler handler;
    synchronized (conn) {
      if (trailersReceived) {
        return;
      }
      ended = err;
      handler = eventHandler;
    }
    endFuture.tryFail(err);
    if (handler != null) {
      handler.handleException(err);
    } else {
      log.error(err.getMessage(), err);
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
    return endFuture.future();
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

  @Override
  public void init(FlowController flowController) {
    this.flowController = flowController;
  }

  @Override
  public void handle(ByteBuf chunk) {
    HttpEventHandler handler;
    synchronized (conn) {
      handler = eventHandler;
    }
    if (handler != null) {
      ByteBuf buffer = VertxByteBufAllocator.DEFAULT.heapBuffer(chunk.readableBytes());
      buffer.writeBytes(chunk, chunk.readerIndex(), chunk.readableBytes());
      Buffer buff = BufferInternal.buffer(buffer);
      handler.handleChunk(buff);
    }
  }

  @Override
  public void destroy() {
  }
}
