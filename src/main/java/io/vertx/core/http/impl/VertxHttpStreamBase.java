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
import io.netty.handler.codec.Headers;
import io.netty.util.concurrent.FutureListener;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.streams.impl.InboundBuffer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class VertxHttpStreamBase<C extends ConnectionBase, S, H extends Headers<CharSequence, CharSequence, H>> {

  protected final C conn;
  protected final VertxInternal vertx;
  protected final ContextInternal context;

  // Client context
  private StreamPriorityBase priority;
  private final InboundBuffer<Object> pending;
  protected boolean writable;
  private long bytesRead;
  private long bytesWritten;
  protected boolean isConnect;

  protected S stream;
  protected abstract void consumeCredits(int len);
  protected abstract void writeFrame(byte type, short flags, ByteBuf payload);
  protected abstract void writeHeaders(H headers, boolean end, int dependency, short weight, boolean exclusive, boolean checkFlush,
                    FutureListener<Void> promise);
  protected abstract void writePriorityFrame(StreamPriorityBase priority);
  protected abstract void writeData_(ByteBuf chunk, boolean end, FutureListener<Void> promise);
  protected abstract void writeReset_(int streamId, long code);
  protected abstract void init_(VertxHttpStreamBase vertxHttpStream, S stream);
  protected abstract int getStreamId();
  protected abstract boolean remoteSideOpen();
  protected abstract boolean hasStream();
  protected abstract MultiMap getEmptyHeaders();
  protected abstract boolean isWritable_();
  protected abstract boolean isTrailersReceived_();

  VertxHttpStreamBase(C conn, ContextInternal context) {
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
        conn.getContext().emit(null, v -> {
          if (remoteSideOpen()) {
            // Handle the HTTP upgrade case
            // buffers are received by HTTP/1 and not accounted by HTTP/2
            consumeCredits(len);
          }
        });
        bytesRead += data.length();
        handleData(data);
      }
    });
    pending.exceptionHandler(context::reportException);
    pending.resume();
  }

  void init(S stream) {
    this.stream = stream;
    synchronized (this) {
      this.init_(this, stream);
      this.writable = this.isWritable_();
    }
  }

  void onClose() {
    conn.flushBytesWritten();
    context.execute(ex -> handleClose());
  }

  void onException(Throwable cause) {
    context.emit(cause, this::handleException);
  }

  void onReset(long code) {
    context.emit(code, this::handleReset);
  }

  void onPriorityChange(StreamPriorityBase newPriority) {
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

  void onHeaders(H headers, StreamPriorityBase streamPriority) {
  }

  void onData(Buffer data) {
    conn.reportBytesRead(data.length());
    context.execute(data, pending::write);
  }

  void onWritabilityChanged() {
    context.emit(null, v -> {
      boolean w;
      synchronized (VertxHttpStreamBase.this) {
        writable = !writable;
        w = writable;
      }
      handleWritabilityChanged(w);
    });
  }

  void onEnd() {
    onEnd(getEmptyHeaders());
  }

  void onEnd(MultiMap trailers) {
    conn.flushBytesRead();
    context.emit(trailers, pending::write);
  }

  public int id() {
    return getStreamId();
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
    writeFrame((byte) type, (short) flags, payload);
  }

  final void writeHeaders(H headers, boolean end, boolean checkFlush, Handler<AsyncResult<Void>> handler) {
    EventLoop eventLoop = conn.getContext().nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      doWriteHeaders(headers, end, checkFlush, handler);
    } else {
      eventLoop.execute(() -> doWriteHeaders(headers, end, checkFlush, handler));
    }
  }

  void doWriteHeaders(H headers, boolean end, boolean checkFlush, Handler<AsyncResult<Void>> handler) {
    FutureListener<Void> promise = handler == null ? null : context.promise(handler);
    writeHeaders(headers, end, priority.getDependency(), priority.getWeight(),
      priority.isExclusive(), checkFlush, promise);
    if (end) {
      endWritten();
    }
  }

  protected void endWritten() {
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
    writeData_(chunk, end, promise);
    if (end) {
      endWritten();
    }
  }

  final void writeReset(long code) {
    EventLoop eventLoop = conn.getContext().nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      doWriteReset(code);
    } else {
      eventLoop.execute(() -> doWriteReset(code));
    }
  }

  protected void doWriteReset(long code) {
    int streamId = getStreamId();
    if (streamId != -1) {
      writeReset_(streamId, code);
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

  synchronized void priority(StreamPriorityBase streamPriority) {
    this.priority = streamPriority;
  }

  synchronized StreamPriorityBase priority() {
    return priority;
  }

  synchronized void updatePriority(StreamPriorityBase priority) {
    if (!this.priority.equals(priority)) {
      this.priority = priority;
      if (hasStream()) {
        writePriorityFrame(priority);
      }
    }
  }

  void handlePriorityChange(StreamPriorityBase newPriority) {
  }

  boolean isTrailersReceived() {
    return isTrailersReceived_();
  }
}
