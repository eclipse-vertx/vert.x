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
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http2.EmptyHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.util.concurrent.FutureListener;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.headers.Http2HeadersAdaptor;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.streams.impl.InboundBuffer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class VertxHttp2Stream<C extends Http2ConnectionBase> {

  private static final MultiMap EMPTY = new Http2HeadersAdaptor(EmptyHttp2Headers.INSTANCE);

  protected final C conn;
  protected final VertxInternal vertx;
  protected final ContextInternal context;
  protected Http2Stream stream;

  // Event loop
  private long bytesRead;
  private long bytesWritten;

  // Client context
  private StreamPriority priority;
  private final InboundBuffer<Object> pending;
  private boolean writable;

  VertxHttp2Stream(C conn, ContextInternal context) {
    this.conn = conn;
    this.vertx = conn.vertx();
    this.context = context;
    this.pending = new InboundBuffer<>(context, 5);
    this.priority = HttpUtils.DEFAULT_STREAM_PRIORITY;

    pending.handler(item -> {
      if (item instanceof MultiMap) {
        conn.reportBytesRead(bytesRead);
        handleEnd((MultiMap) item);
      } else {
        Buffer data = (Buffer) item;
        int len = data.length();
        conn.getContext().dispatch(null, v -> conn.consumeCredits(this.stream, len));
        bytesRead += len;
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
    conn.reportBytesWritten(bytesWritten);
    context.schedule(v -> this.handleClose());
  }

  void onError(Throwable cause) {
    context.dispatch(cause, this::handleException);
  }

  void onReset(long code) {
    context.dispatch(code, this::handleReset);
  }

  void onPriorityChange(StreamPriority newPriority) {
    context.dispatch(newPriority, priority -> {
      if (!this.priority.equals(priority)) {
        this.priority = priority;
        handlePriorityChange(priority);
      }
    });
  }

  void onCustomFrame(HttpFrame frame) {
    context.dispatch(frame, this::handleCustomFrame);
  }

  void onHeaders(Http2Headers headers, StreamPriority streamPriority) {
  }

  void onData(Buffer data) {
    context.dispatch(data, pending::write);
  }

  void onWritabilityChanged() {
    context.dispatch(null, v -> {
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
    context.dispatch(trailers, pending::write);
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

  void flush() {
    conn.flush(stream);
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

  void doWriteData(ByteBuf chunk, boolean end, Handler<AsyncResult<Void>> handler) {
    bytesWritten += chunk.readableBytes();
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

  void resolveFile(String filename, long offset, long length, Handler<AsyncResult<AsyncFile>> resultHandler) {
    File file_ = vertx.resolveFile(filename);
    if (!file_.exists()) {
      resultHandler.handle(Future.failedFuture(new FileNotFoundException()));
      return;
    }

    //We open the fileName using a RandomAccessFile to make sure that this is an actual file that can be read.
    //i.e is not a directory
    try(RandomAccessFile raf = new RandomAccessFile(file_, "r")) {
      FileSystem fs = conn.vertx().fileSystem();
      fs.open(filename, new OpenOptions().setCreate(false).setWrite(false), ar -> {
        if (ar.succeeded()) {
          AsyncFile file = ar.result();
          long contentLength = Math.min(length, file_.length() - offset);
          file.setReadPos(offset);
          file.setReadLength(contentLength);
        }
        resultHandler.handle(ar);
      });
    } catch (IOException e) {
      resultHandler.handle(Future.failedFuture(e));
    }
  }
}
