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
class Http2ClientStreamImpl implements HttpClientStream {

  private final Http2ClientConnection conn;
  private final ContextInternal context;
  Http2ClientStream stream;

  Handler<HttpResponseHead> headHandler;
  Handler<Buffer> chunkHandler;
  Handler<MultiMap> endHandler;
  Handler<StreamPriority> priorityHandler;
  Handler<Void> drainHandler;
  Handler<Void> continueHandler;
  Handler<MultiMap> earlyHintsHandler;
  Handler<HttpFrame> unknownFrameHandler;
  Handler<Throwable> exceptionHandler;
  Handler<HttpClientPush> pushHandler;
  Handler<Void> closeHandler;

  TracingPolicy tracingPolicy;
  boolean decompressionSupported;
  ClientMetrics clientMetrics;
  boolean push;

  Http2ClientStreamImpl(Http2ClientConnection conn,
                        ContextInternal context,
                        TracingPolicy tracingPolicy,
                        boolean decompressionSupported,
                        ClientMetrics clientMetrics,
                        boolean push) {
    this.context = context;
    this.conn = conn;

    this.tracingPolicy = tracingPolicy;
    this.decompressionSupported = decompressionSupported;
    this.clientMetrics = clientMetrics;
    this.push = push;
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
  public void doPause() {
    stream.doPause();
  }

  @Override
  public void doFetch(long amount) {
    stream.doFetch(amount);
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
    Http2ClientStream s = stream;
    return s != null && !s.isWritable();
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
    return stream.priority();
  }

  @Override
  public void updatePriority(StreamPriority streamPriority) {
    stream.updatePriority(streamPriority);
  }

  @Override
  public HttpVersion version() {
    return HttpVersion.HTTP_2;
  }


  @Override
  public Future<Void> writeHead(HttpRequestHead request, boolean chunked, ByteBuf buf, boolean end, StreamPriority priority, boolean connect) {
    PromiseInternal<Void> promise = context.promise();

    stream = new Http2ClientStream(conn, context, tracingPolicy, decompressionSupported, clientMetrics);
    stream.impl = this;

    stream.writeHeaders(request, buf, end, priority, promise);
    return promise.future();
  }

  @Override
  public Future<Void> writeBuffer(ByteBuf buf, boolean end) {
    Promise<Void> promise = context.promise();
    stream.writeData(buf, end, promise);
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
    Http2ClientStream s = stream;
    if (s != null) {
      return s.writeReset(code);
    } else {
      return null;
    }
  }

  @Override
  public HttpClientConnectionInternal connection() {
    return (HttpClientConnectionInternal) conn;
  }
}
