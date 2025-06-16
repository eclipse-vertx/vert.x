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
package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.streams.WriteStream;
import io.vertx.core.tracing.TracingPolicy;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http2ClientStreamImpl extends Http2ClientStream implements HttpClientStream {

  private Handler<HttpResponseHead> headHandler;
  private Handler<Buffer> chunkHandler;
  private Handler<MultiMap> endHandler;
  private Handler<StreamPriority> priorityHandler;
  private Handler<Void> drainHandler;
  private Handler<Void> continueHandler;
  private Handler<MultiMap> earlyHintsHandler;
  private Handler<HttpFrame> unknownFrameHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<HttpClientPush> pushHandler;
  private Handler<Void> closeHandler;

  Http2ClientStreamImpl(Http2ClientConnection conn,
                        ContextInternal context,
                        TracingPolicy tracingPolicy,
                        boolean decompressionSupported,
                        ClientMetrics clientMetrics,
                        boolean push) {
    super(conn, context, tracingPolicy, decompressionSupported, clientMetrics);
  }

  @Override
  public void closeHandler(Handler<Void> handler) {
    closeHandler = handler;
  }

  @Override
  public void continueHandler(Handler<Void> handler) {
    continueHandler = handler;
  }

  @Override
  public void earlyHintsHandler(Handler<MultiMap> handler) {
    earlyHintsHandler = handler;
  }

  @Override
  public void unknownFrameHandler(Handler<HttpFrame> handler) {
    unknownFrameHandler = handler;
  }

  @Override
  public void pushHandler(Handler<HttpClientPush> handler) {
    pushHandler = handler;
  }

  @Override
  public Http2ClientStreamImpl drainHandler(Handler<Void> handler) {
    drainHandler = handler;
    return this;
  }

  @Override
  public Http2ClientStreamImpl exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }

  @Override
  public WriteStream<Buffer> setWriteQueueMaxSize(int maxSize) {
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    return !isNotWritable();
  }

  @Override
  public boolean isNotWritable() {
    return !isWritable();
  }

  @Override
  public void headHandler(Handler<HttpResponseHead> handler) {
    headHandler = handler;
  }

  @Override
  public void chunkHandler(Handler<Buffer> handler) {
    chunkHandler = handler;
  }

  @Override
  public void priorityHandler(Handler<StreamPriority> handler) {
    priorityHandler = handler;
  }

  @Override
  public void endHandler(Handler<MultiMap> handler) {
    endHandler = handler;
  }

  @Override
  public StreamPriority priority() {
    return super.priority();
  }

  @Override
  public void updatePriority(StreamPriority streamPriority) {
    super.updatePriority(streamPriority);
  }

  @Override
  public HttpVersion version() {
    return HttpVersion.HTTP_2;
  }

  @Override
  void handleEnd(MultiMap trailers) {
    if (endHandler != null) {
      endHandler.handle(trailers);
    }
  }

  @Override
  void handleData(Buffer buf) {
    if (chunkHandler != null) {
      chunkHandler.handle(buf);
    }
  }

  @Override
  void handleReset(long errorCode) {
    handleException(new StreamResetException(errorCode));
  }

  @Override
  void handleWriteQueueDrained() {
    Handler<Void> handler = drainHandler;
    if (handler != null) {
      context.dispatch(null, handler);
    }
  }

  @Override
  void handleCustomFrame(HttpFrame frame) {
    if (unknownFrameHandler != null) {
      unknownFrameHandler.handle(frame);
    }
  }


  @Override
  void handlePriorityChange(StreamPriority streamPriority) {
    if (priorityHandler != null) {
      priorityHandler.handle(streamPriority);
    }
  }

  @Override
  void handleContinue() {
    if (continueHandler != null) {
      continueHandler.handle(null);
    }
  }

  @Override
  void handlePush(HttpClientPush push) {
    if (pushHandler != null) {
      pushHandler.handle(push);
    } else {
      // Must reset
      throw new UnsupportedOperationException();
    }
  }

  @Override
  void handleEarlyHints(MultiMap headers) {
    if (earlyHintsHandler != null) {
      earlyHintsHandler.handle(headers);
    }
  }

  @Override
  void handleException(Throwable exception) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(exception);
    }
  }

  @Override
  void handleHead(HttpResponseHead response) {
    if (headHandler != null) {
      context.emit(response, headHandler);
    }
  }

  @Override
  void handleClose() {
    if (closeHandler != null) {
      closeHandler.handle(null);
    }
  }

  @Override
  public Future<Void> writeHead(HttpRequestHead request, boolean chunked, ByteBuf buf, boolean end, StreamPriority priority, boolean connect) {
    PromiseInternal<Void> promise = context.promise();
    writeHeaders(request, buf, end, priority, promise);
    return promise.future();
  }

  @Override
  public Future<Void> writeBuffer(ByteBuf buf, boolean end) {
    Promise<Void> promise = context.promise();
    writeData(buf, end, promise);
    return promise.future();
  }

  @Override
  public ContextInternal getContext() {
    return context;
  }

  @Override
  public void doSetWriteQueueMaxSize(int size) {
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
    return writeReset(code);
  }

  @Override
  public HttpClientConnectionInternal connection() {
    return (HttpClientConnectionInternal) conn;
  }
}
