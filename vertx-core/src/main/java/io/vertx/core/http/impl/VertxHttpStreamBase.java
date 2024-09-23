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
import io.netty.util.concurrent.FutureListener;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.http.impl.headers.VertxHttpHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.concurrent.InboundMessageQueue;
import io.vertx.core.internal.concurrent.OutboundMessageQueue;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.net.impl.MessageWrite;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class VertxHttpStreamBase<C extends ConnectionBase, S> {

  private final OutboundMessageQueue<MessageWrite> outboundQueue;
  private final InboundMessageQueue<Object> inboundQueue;
  protected final C conn;
  protected final VertxInternal vertx;
  protected final ContextInternal context;

  // Client context
  protected boolean writable;
  private StreamPriorityBase priority;
  private long bytesRead;
  private long bytesWritten;
  protected boolean isConnect;
  private Throwable failure;

  protected S stream;
  protected abstract void consumeCredits(S stream, int len);
  protected abstract void writeFrame(S stream, byte type, short flags, ByteBuf payload, Promise<Void> promise);
  protected abstract void writeHeaders(S stream, VertxHttpHeaders headers, boolean end, StreamPriorityBase priority, boolean checkFlush, FutureListener<Void> promise);
  protected abstract void writePriorityFrame(StreamPriorityBase priority);
  protected abstract void writeData_(S stream, ByteBuf chunk, boolean end, FutureListener<Void> promise);
  protected abstract void writeReset_(int streamId, long code);
  protected abstract void init_(VertxHttpStreamBase vertxHttpStream, S stream);
  protected abstract int getStreamId();
  protected abstract boolean remoteSideOpen(S stream);
  protected abstract MultiMap getEmptyHeaders();
  protected abstract boolean isWritable_();
  protected abstract boolean isTrailersReceived();
  protected abstract StreamPriorityBase createDefaultStreamPriority();

  VertxHttpStreamBase(C conn, ContextInternal context) {
    this.conn = conn;
    this.vertx = conn.vertx();
    this.context = context;
    this.inboundQueue = new InboundMessageQueue<>(conn.channel().eventLoop(), context) {
      @Override
      protected void handleMessage(Object item) {
        if (item instanceof MultiMap) {
          handleEnd((MultiMap) item);
        } else {
          Buffer data = (Buffer) item;
          int len = data.length();
          conn.context().emit(null, v -> {
            if (remoteSideOpen(stream)) {
              // Handle the HTTP upgrade case
              // buffers are received by HTTP/1 and not accounted by HTTP/2
              consumeCredits(stream, len);
            }
          });
          handleData(data);
        }
      }
    };
    this.priority = createDefaultStreamPriority();
    this.isConnect = false;
    this.writable = true;
    this.outboundQueue = new OutboundMessageQueue<>(conn.context().nettyEventLoop()) {
      // TODO implement stop drain to optimize flushes ?
      @Override
      public boolean test(MessageWrite msg) {
        if (writable) {
          msg.write();
          return true;
        } else {
          return false;
        }
      }
      @Override
      protected void disposeMessage(MessageWrite messageWrite) {
        Throwable cause = failure;
        if (cause == null) {
          cause = HttpUtils.STREAM_CLOSED_EXCEPTION;
        }
        messageWrite.cancel(cause);
      }
      @Override
      protected void writeQueueDrained() {
        context.emit(VertxHttpStreamBase.this, VertxHttpStreamBase::handleWriteQueueDrained);
      }
    };
  }

  void init(S stream) {
    synchronized (this) {
      this.stream = stream;
    }
    this.writable = this.isWritable_();
    this.init_(this, stream);
  }

  void onClose() {
    conn.flushBytesWritten();
    context.execute(ex -> handleClose());
    outboundQueue.close();
  }

  void onException(Throwable cause) {
    failure = cause;
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

  void onHeaders(VertxHttpHeaders headers, StreamPriorityBase streamPriority) {
  }

  void onData(Buffer data) {
    bytesRead += data.length();
    conn.reportBytesRead(data.length());
    inboundQueue.write(data);
  }

  void onWritabilityChanged() {
    writable = !writable;
    if (writable) {
      outboundQueue.drain();
    }
  }

  void onEnd() {
    onEnd(getEmptyHeaders());
  }

  void onEnd(MultiMap trailers) {
    conn.flushBytesRead();
    inboundQueue.write(trailers);
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

  public boolean isWritable() {
    return outboundQueue.isWritable();
  }

  public void write(MessageWrite write) {
    outboundQueue.write(write);
  }

  public void doPause() {
    inboundQueue.pause();
  }

  public void doFetch(long amount) {
    inboundQueue.fetch(amount);
  }

  public boolean isNotWritable() {
    return !outboundQueue.isWritable();
  }

  public final Future<Void> writeFrame(int type, int flags, ByteBuf payload) {
    Promise<Void> promise = context.promise();
    EventLoop eventLoop = conn.context().nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      doWriteFrame(type, flags, payload, promise);
    } else {
      eventLoop.execute(() -> doWriteFrame(type, flags, payload, promise));
    }
    return promise.future();
  }

  public final void writeFrame(int type, int flags, ByteBuf payload, Promise<Void> promise) {
    EventLoop eventLoop = conn.context().nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      doWriteFrame(type, flags, payload, promise);
    } else {
      eventLoop.execute(() -> doWriteFrame(type, flags, payload, promise));
    }
  }

  private void doWriteFrame(int type, int flags, ByteBuf payload, Promise<Void> promise) {
    writeFrame(stream, (byte) type, (short) flags, payload, promise);
  }

  final void writeHeaders(VertxHttpHeaders headers, boolean first, boolean end, boolean checkFlush, Promise<Void> promise) {
    if (first) {
      EventLoop eventLoop = conn.context().nettyEventLoop();
      if (eventLoop.inEventLoop()) {
        doWriteHeaders(headers, end, checkFlush, promise);
      } else {
        eventLoop.execute(() -> doWriteHeaders(headers, end, checkFlush, promise));
      }
    } else {
      outboundQueue.write(new MessageWrite() {
        @Override
        public void write() {
          doWriteHeaders(headers, end, checkFlush, promise);
        }
        @Override
        public void cancel(Throwable cause) {
          promise.fail(cause);
        }
      });
    }
  }

  void doWriteHeaders(VertxHttpHeaders headers, boolean end, boolean checkFlush, Promise<Void> promise) {
    if (end) {
      endWritten();
    }
    writeHeaders(stream, headers, end, priority, checkFlush, (FutureListener<Void>) promise);
  }

  protected void endWritten() {
  }

  final void writeData(ByteBuf chunk, boolean end, Promise<Void> promise) {
    outboundQueue.write(new MessageWrite() {
      @Override
      public void write() {
        doWriteData(chunk, end, promise);
      }
      @Override
      public void cancel(Throwable cause) {
        promise.fail(cause);
      }
    });
  }

  void doWriteData(ByteBuf buf, boolean end, Promise<Void> promise) {
    ByteBuf chunk;
    if (buf == null && end) {
      chunk = Unpooled.EMPTY_BUFFER;
    } else {
      chunk = buf;
    }
    int numOfBytes = chunk.readableBytes();
    bytesWritten += numOfBytes;
    conn.reportBytesWritten(numOfBytes);
    if (end) {
      endWritten();
    }
    writeData_(stream, chunk, end, (FutureListener<Void>)promise);
  }

  final void writeReset(long code) {
    EventLoop eventLoop = conn.context().nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      doWriteReset(code);
    } else {
      eventLoop.execute(() -> doWriteReset(code));
    }
  }

  protected void doWriteReset(long code) {
    int streamId;
    synchronized (this) {
      streamId = getStreamId();
    }
    if (streamId != -1) {
      writeReset_(streamId, code);
    } else {
      // Reset happening before stream allocation
      handleReset(code);
    }
  }

  void handleWriteQueueDrained() {
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
      if (stream != null) {
        writePriorityFrame(priority);
      }
    }
  }

  void handlePriorityChange(StreamPriorityBase newPriority) {
  }
}
