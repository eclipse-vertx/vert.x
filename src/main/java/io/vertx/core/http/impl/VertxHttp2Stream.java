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
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http2.EmptyHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.util.concurrent.FutureListener;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.headers.Http2HeadersAdaptor;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.streams.impl.InboundBuffer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class VertxHttp2Stream<C extends Http2ConnectionBase> {

  private static final MultiMap EMPTY = new Http2HeadersAdaptor(EmptyHttp2Headers.INSTANCE);

  protected final C conn;
  protected final VertxInternal vertx;
  protected final ContextInternal context;
  protected Http2Stream stream;

  // Client context
  private StreamPriority priority;
  private final InboundBuffer<Object> pending;
  private boolean writable;
  private long bytesRead;
  private long bytesWritten;
  protected boolean isConnect;

  VertxHttp2Stream(C conn, ContextInternal context) {
    this.conn = conn;
    this.vertx = conn.vertx();
    this.context = context;
    this.pending = new InboundBuffer<>(context, 5);
    this.priority = HttpUtils.DEFAULT_STREAM_PRIORITY;
    this.writable = true;
    this.isConnect = false;
    pending.handler(item -> {
      if (item instanceof MultiMap) {
        handleEnd((MultiMap) item);
      } else {
        Buffer data = (Buffer) item;
        int len = data.length();
        conn.getContext().emit(null, v -> conn.consumeCredits(this.stream, len));
        bytesRead += data.length();
        handleData(data);
      }
    });
    pending.exceptionHandler(context::reportException);
    pending.resume();
  }

  void init(Http2Stream stream) {
    synchronized (this) {
      this.stream = stream;
      this.writable = this.conn.handler.encoder().flowController().isWritable(stream);
    }
    stream.setProperty(conn.streamKey, this);
  }

  void onClose() {
    conn.flushBytesWritten();
    context.execute(v -> this.handleClose());
  }

  void onError(Throwable cause) {
    context.emit(cause, this::handleException);
  }

  void onReset(long code) {
    context.emit(code, this::handleReset);
  }

  void onPriorityChange(StreamPriority newPriority) {
    context.emit(newPriority, priority -> {
      if (!this.priority.equals(priority)) {
        this.priority = priority;
        handlePriorityChange(priority);
      }
    });
  }

  void onCustomFrame(HttpFrame frame) {
    context.emit(frame, this::handleCustomFrame);
  }

  void onHeaders(Http2Headers headers, StreamPriority streamPriority) {
  }

  void onData(Buffer data) {
    conn.reportBytesRead(data.length());
    context.emit(data, pending::write);
  }

  void onWritabilityChanged() {
    context.emit(null, v -> {
      boolean w;
      synchronized (VertxHttp2Stream.this) {
        writable = !writable;
        w = writable;
      }
      handleWritabilityChanged(w);
    });
  }

  void onEnd() {
    onEnd(EMPTY);
  }

  void onEnd(MultiMap trailers) {
    conn.flushBytesRead();
    context.emit(trailers, pending::write);
    if (isConnect) {
      doWriteData(Unpooled.EMPTY_BUFFER, true, null);
    }
  }

  public int id() {
    return stream.id();
  }

  long bytesWritten() {
    return bytesWritten;
  }

  long bytesRead() {
    return bytesRead;
  }

  public void doPause() {
    pending.pause();
  }

  public void doFetch(long amount) {
    pending.fetch(amount);
  }

  public synchronized boolean isNotWritable() {
    return !writable;
  }

  public final void writeFrame(int type, int flags, ByteBuf payload) {
    EventLoop eventLoop = conn.getContext().nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      doWriteFrame(type, flags, payload);
    } else {
      eventLoop.execute(() -> doWriteFrame(type, flags, payload));
    }
  }

  private void doWriteFrame(int type, int flags, ByteBuf payload) {
    conn.handler.writeFrame(stream, (byte) type, (short) flags, payload);
  }

  final void writeHeaders(Http2Headers headers, boolean end, Handler<AsyncResult<Void>> handler) {
    EventLoop eventLoop = conn.getContext().nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      doWriteHeaders(headers, end, handler);
    } else {
      eventLoop.execute(() -> doWriteHeaders(headers, end, handler));
    }
  }

  void doWriteHeaders(Http2Headers headers, boolean end, Handler<AsyncResult<Void>> handler) {
    FutureListener<Void> promise = handler == null ? null : context.promise(handler);
    conn.handler.writeHeaders(stream, headers, end, priority.getDependency(), priority.getWeight(), priority.isExclusive(), promise);
  }

  private void writePriorityFrame(StreamPriority priority) {
    conn.handler.writePriority(stream, priority.getDependency(), priority.getWeight(), priority.isExclusive());
  }

  final void writeData(ByteBuf chunk, boolean end, Handler<AsyncResult<Void>> handler) {
    ContextInternal ctx = conn.getContext();
    EventLoop eventLoop = ctx.nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      doWriteData(chunk, end, handler);
    } else {
      eventLoop.execute(() -> doWriteData(chunk, end, handler));
    }
  }

  void doWriteData(ByteBuf buf, boolean end, Handler<AsyncResult<Void>> handler) {
    ByteBuf chunk;
    if (buf == null && end) {
      chunk = Unpooled.EMPTY_BUFFER;
    } else {
      chunk = buf;
    }
    int numOfBytes = chunk.readableBytes();
    bytesWritten += numOfBytes;
    conn.reportBytesWritten(numOfBytes);
    FutureListener<Void> promise = handler == null ? null : context.promise(handler);
    conn.handler.writeData(stream, chunk, end, promise);
  }

  final void writeReset(long code) {
    EventLoop eventLoop = conn.getContext().nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      doWriteReset(code);
    } else {
      eventLoop.execute(() -> doWriteReset(code));
    }
  }

  private void doWriteReset(long code) {
    int streamId;
    synchronized (this) {
      streamId = stream != null ? stream.id() : -1;
    }
    if (streamId != -1) {
      conn.handler.writeReset(streamId, code);
    } else {
      // Reset happening before stream allocation
      handleReset(code);
    }
  }

  void handleWritabilityChanged(boolean writable) {
  }

  void handleData(Buffer buf) {
  }

  void handleCustomFrame(HttpFrame frame) {
  }

  void handleEnd(MultiMap trailers) {
  }

  void handleReset(long errorCode) {
  }

  void handleException(Throwable cause) {
  }

  void handleClose() {
  }

  synchronized void priority(StreamPriority streamPriority) {
    this.priority = streamPriority;
  }

  synchronized StreamPriority priority() {
    return priority;
  }

  synchronized void updatePriority(StreamPriority priority) {
    if (!this.priority.equals(priority)) {
      this.priority = priority;
      if (stream != null) {
        writePriorityFrame(priority);
      }
    }
  }

  void handlePriorityChange(StreamPriority newPriority) {
  }
}
