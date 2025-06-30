/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl.http2;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaderValidationUtil;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.http.impl.HttpClientConnection;
import io.vertx.core.http.impl.HttpClientStream;
import io.vertx.core.http.impl.HttpRequestHead;
import io.vertx.core.http.impl.HttpResponseHead;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.tracing.TracingPolicy;

import java.util.Iterator;
import java.util.Map;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ClientStreamImpl implements HttpClientStream, Http2ClientStreamHandler {

  private final Http2ClientConnection conn;
  private final ContextInternal context;
  public Http2ClientStream stream;

  private Handler<HttpResponseHead> headHandler;
  private Handler<Buffer> chunkHandler;
  private Handler<MultiMap> trailerHandler;
  private Handler<Void> endHandler;
  private Handler<StreamPriority> priorityHandler;
  private Handler<Void> drainHandler;
  private Handler<Void> continueHandler;
  private Handler<MultiMap> earlyHintsHandler;
  private Handler<HttpFrame> unknownFrameHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Http2ClientPush> pushHandler;
  private Handler<Void> closeHandler;

  private TracingPolicy tracingPolicy;
  private boolean decompressionSupported;
  private ClientMetrics clientMetrics;

  public Http2ClientStreamImpl(Http2ClientConnection conn,
                        ContextInternal context,
                        TracingPolicy tracingPolicy,
                        boolean decompressionSupported,
                        ClientMetrics clientMetrics) {
    this.context = context;
    this.conn = conn;

    this.tracingPolicy = tracingPolicy;
    this.decompressionSupported = decompressionSupported;
    this.clientMetrics = clientMetrics;
  }

  @Override
  public int id() {
    return stream.id();
  }

  @Override
  public Object metric() {
    return stream.metric();
  }

  @Override
  public Object trace() {
    return stream.trace();
  }

  @Override
  public Future<Void> writeFrame(int type, int flags, ByteBuf payload) {
    return stream.writeFrame(type, flags, payload);
  }

  @Override
  public HttpClientStream pause() {
    stream.pause();
    return this;
  }

  @Override
  public HttpClientStream fetch(long amount) {
    stream.fetch(amount);
    return this;
  }

  @Override
  public HttpClientStream resume() {
    stream.fetch(Long.MAX_VALUE);
    return this;
  }

  @Override
  public HttpClientStream closeHandler(Handler<Void> handler) {
    closeHandler = handler;
    return this;
  }

  @Override
  public HttpClientStream continueHandler(Handler<Void> handler) {
    continueHandler = handler;
    return this;
  }

  @Override
  public HttpClientStream earlyHintsHandler(Handler<MultiMap> handler) {
    earlyHintsHandler = handler;
    return this;
  }

  @Override
  public HttpClientStream unknownFrameHandler(Handler<HttpFrame> handler) {
    unknownFrameHandler = handler;
    return this;
  }

  @Override
  public HttpClientStream pushHandler(Handler<Http2ClientPush> handler) {
    pushHandler = handler;
    return this;
  }

  @Override
  public HttpClientStream drainHandler(Handler<Void> handler) {
    drainHandler = handler;
    return this;
  }

  @Override
  public Http2ClientStreamImpl exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }

  @Override
  public HttpClientStream setWriteQueueMaxSize(int maxSize) {
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    Http2ClientStream s = stream;
    return s != null && !s.isWritable();
  }

  @Override
  public HttpClientStream headHandler(Handler<HttpResponseHead> handler) {
    headHandler = handler;
    return this;
  }

  @Override
  public HttpClientStream handler(Handler<Buffer> handler) {
    chunkHandler = handler;
    return this;
  }

  @Override
  public HttpClientStream priorityHandler(Handler<StreamPriority> handler) {
    priorityHandler = handler;
    return this;
  }

  @Override
  public HttpClientStream endHandler(Handler<Void> handler) {
    endHandler = handler;
    return this;
  }

  @Override
  public HttpClientStream trailersHandler(Handler<MultiMap> handler) {
    trailerHandler = handler;
    return this;
  }

  @Override
  public StreamPriority priority() {
    return stream.priority();
  }

  @Override
  public HttpClientStream updatePriority(StreamPriority streamPriority) {
    stream.updatePriority(streamPriority);
    return this;
  }

  @Override
  public HttpVersion version() {
    return HttpVersion.HTTP_2;
  }


  @Override
  public Future<Void> writeHead(HttpRequestHead request, boolean chunked, ByteBuf buf, boolean end, StreamPriority priority, boolean connect) {
    PromiseInternal<Void> promise = context.promise();
    stream = new Http2ClientStream(conn, context, tracingPolicy, decompressionSupported, clientMetrics);
    stream.handler(this);
    MultiMap headers = request.headers;
    if (headers != null) {
      headers.remove(HttpHeaders.TRANSFER_ENCODING);
    }
    stream.writeHeaders(request, buf, end, priority, promise);
    return promise.future();
  }

  @Override
  public Future<Void> write(ByteBuf buf, boolean end) {
    Promise<Void> promise = context.promise();
    stream.writeData(buf, end, promise);
    return promise.future();
  }

  @Override
  public ContextInternal context() {
    return context;
  }

  @Override
  public Future<Void> reset(Throwable cause) {
    long code;
    if (cause instanceof StreamResetException) {
      code = ((StreamResetException) cause).getCode();
    } else if (cause instanceof java.util.concurrent.TimeoutException) {
      code = 0x08L; // CANCEL
    } else {
      code = 0L;
    }
    Http2ClientStream s = stream;
    if (s != null) {
      return s.writeReset(code);
    } else {
      return null;
    }
  }

  @Override
  public HttpClientConnection connection() {
    // That's a bit a hack to avoid implementing a proxy of HttpClientConnection
    return (HttpClientConnection) conn;
  }

  @Override
  public void handleDrained() {
    Handler<Void> handler = drainHandler;
    if (handler != null) {
      handler.handle(null);
    }
  }

  @Override
  public void handleHeaders(Http2HeadersMultiMap headers) {
    Handler<HttpResponseHead> handler = headHandler;
    if (handler != null) {
      int status = headers.status();
      String statusMessage = HttpResponseStatus.valueOf(status).reasonPhrase();
      HttpResponseHead response = new HttpResponseHead(
        HttpVersion.HTTP_2,
        status,
        statusMessage,
        headers);
      handler.handle(response);
    }
  }

  @Override
  public void handleContinue() {
    Handler<Void> handler = continueHandler;
    if (handler != null) {
      handler.handle(null);
    }
  }

  @Override
  public void handlePush(Http2ClientPush push) {
    Handler<Http2ClientPush> handler = pushHandler;
    if (handler != null) {
      handler.handle(push);
    }
  }

  @Override
  public void handleEarlyHints(MultiMap headers) {
    Handler<MultiMap> handler = earlyHintsHandler;
    if (handler != null) {
      handler.handle(headers);
    }
  }

  @Override
  public void handleReset(long errorCode) {
    Handler<Throwable> handler = exceptionHandler;
    if (handler != null) {
      handler.handle(new StreamResetException(errorCode));
    }
  }

  @Override
  public void handleException(Throwable cause) {
    Handler<Throwable> handler = exceptionHandler;
    if (handler != null) {
      handler.handle(cause);
    }
  }

  @Override
  public void handleClose() {
    Handler<Void> handler = closeHandler;
    if (handler != null) {
      handler.handle(null);
    }
  }

  @Override
  public void handleData(Buffer data) {
    Handler<Buffer> handler = chunkHandler;
    if (handler != null) {
      handler.handle(data);
    }
  }

  @Override
  public void handleTrailers(MultiMap trailers) {
    Handler<MultiMap> handler1 = trailerHandler;
    if (handler1 != null) {
      context.dispatch(trailers, handler1);
    }
    Handler<Void> handler2 = endHandler;
    if (handler2 != null) {
      context.dispatch(null, handler2);
    }
  }

  @Override
  public void handleCustomFrame(HttpFrame frame) {
    Handler<HttpFrame> handler = unknownFrameHandler;
    if (handler != null) {
      context.dispatch(frame, handler);
    }
  }

  @Override
  public void handlePriorityChange(StreamPriority streamPriority) {
    Handler<StreamPriority> handler = priorityHandler;
    if (handler != null) {
      context.dispatch(streamPriority, handler);
    }
  }
}
