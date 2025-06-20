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

package io.vertx.core.http.impl.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http2.EmptyHttp2Headers;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.HttpFrameImpl;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.concurrent.InboundMessageQueue;
import io.vertx.core.internal.concurrent.OutboundMessageQueue;
import io.vertx.core.net.impl.MessageWrite;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class Http2StreamBase {

  private static final MultiMap EMPTY = new Http2HeadersMultiMap(EmptyHttp2Headers.INSTANCE);

  private final OutboundMessageQueue<MessageWrite> outboundQueue;
  private final InboundMessageQueue<Object> inboundQueue;
  private final Http2Connection conn;
  protected final VertxInternal vertx;
  protected final ContextInternal context;
  protected int id;

  // Client context
  private boolean writable;
  private StreamPriority priority;
  private long bytesRead;
  private long bytesWritten;
  protected boolean isConnect;
  private Throwable failure;
  private long reset = -1L;

  Http2StreamBase(Http2Connection conn, ContextInternal context) {
    this.conn = conn;
    this.vertx = context.owner();
    this.context = context;
    this.id = -1;
    this.inboundQueue = new InboundMessageQueue<>(conn.context().eventLoop(), context.executor()) {
      @Override
      protected void handleMessage(Object item) {
        if (item instanceof MultiMap) {
          handleEnd((MultiMap) item);
        } else {
          Buffer data = (Buffer) item;
          int len = data.length();
          conn.context().emit(len, v -> conn.consumeCredits(id, v));
          handleData(data);
        }
      }
    };
    this.priority = HttpUtils.DEFAULT_STREAM_PRIORITY;
    this.isConnect = false;
    this.writable = true;
    this.outboundQueue = new OutboundMessageQueue<>(conn.context().executor()) {
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
      protected void handleDispose(MessageWrite messageWrite) {
        Throwable cause = failure;
        if (cause == null) {
          cause = HttpUtils.STREAM_CLOSED_EXCEPTION;
        }
        messageWrite.cancel(cause);
      }
      @Override
      protected void handleDrained() {
        context.emit(Http2StreamBase.this, Http2StreamBase::handleWriteQueueDrained);
      }
    };
  }

  public ContextInternal context() {
    return context;
  }

  // Should use generic for Http2StreamHandler
  public abstract Http2StreamHandler handler();

  public abstract Http2StreamBase handler(Http2StreamHandler handler);

  public void init(int streamId, boolean writable) {
    synchronized (this) {
      this.id = streamId;
      this.writable = writable;
    }
  }

  public void onClose() {
    conn.flushBytesWritten();
    context.execute(ex -> handleClose());
    outboundQueue.close();
  }

  public void onReset(long code) {
    reset = code;
    context.emit(code, this::handleReset);
  }

  public abstract void onHeaders(Http2HeadersMultiMap headers, StreamPriority streamPriority);

  public void onException(Throwable cause) {
    failure = cause;
    context.emit(cause, this::handleException);
  }

  public void onPriorityChange(StreamPriority newPriority) {
    context.emit(newPriority, priority -> {
      if (!this.priority.equals(priority)) {
        this.priority = priority;
        handlePriorityChange(priority);
      }
    });
  }

  public void onCustomFrame(int type, int flags, Buffer payload) {
    context.emit(new HttpFrameImpl(type, flags, payload), this::handleCustomFrame);
  }

  public void onData(Buffer data) {
    bytesRead += data.length();
    conn.reportBytesRead(data.length());
    inboundQueue.write(data);
  }

  public void onWritabilityChanged() {
    writable = !writable;
    if (writable) {
      outboundQueue.tryDrain();
    }
  }

  public void onEnd() {
    onEnd(EMPTY);
  }

  void onEnd(MultiMap trailers) {
    conn.flushBytesRead();
    inboundQueue.write(trailers);
  }

  public int id() {
    return id;
  }

  public long bytesWritten() {
    return bytesWritten;
  }

  public long bytesRead() {
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
      conn.writeFrame(id, type, flags, payload, promise);
    } else {
      eventLoop.execute(() -> conn.writeFrame(id, type, flags, payload, promise));
    }
    return promise.future();
  }

  public final void writeHeaders(Http2HeadersMultiMap headers, boolean first, boolean end, boolean checkFlush, Promise<Void> promise) {
    if (first) {
      EventLoop eventLoop = conn.context().nettyEventLoop();
      if (eventLoop.inEventLoop()) {
        writeHeaders0(headers, end, checkFlush, promise);
      } else {
        eventLoop.execute(() -> writeHeaders0(headers, end, checkFlush, promise));
      }
    } else {
      outboundQueue.write(new MessageWrite() {
        @Override
        public void write() {
          writeHeaders0(headers, end, checkFlush, promise);
        }
        @Override
        public void cancel(Throwable cause) {
          promise.fail(cause);
        }
      });
    }
  }

  void writeHeaders0(Http2HeadersMultiMap headers, boolean end, boolean checkFlush, Promise<Void> promise) {
    if (reset != -1L) {
      if (promise != null) {
        promise.fail("Stream reset");
      }
      return;
    }
    if (failure != null) {
      if (promise != null) {
        promise.fail(failure);
      }
      return;
    }
    if (end) {
      endWritten();
    }
    conn.writeHeaders(id, headers, priority, end, checkFlush, promise);
  }

  // MAYBE NOT NECESSARY
  protected void endWritten() {
  }

  public final void writeData(ByteBuf chunk, boolean end, Promise<Void> promise) {
    outboundQueue.write(new MessageWrite() {
      @Override
      public void write() {
        writeData0(chunk, end, promise);
      }
      @Override
      public void cancel(Throwable cause) {
        promise.fail(cause);
      }
    });
  }

  void writeData0(ByteBuf buf, boolean end, Promise<Void> promise) {
    if (reset != -1L) {
      promise.fail("Stream reset");
      return;
    }
    if (failure != null) {
      promise.fail(failure);
      return;
    }
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
    conn.writeData(id, chunk, end, promise);
  }

  public final Future<Void> writeReset(long code) {
    if (code < 0L) {
      throw new IllegalArgumentException("Invalid reset code value");
    }
    Promise<Void> promise = context.promise();
    EventLoop eventLoop = conn.context().nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      writeReset0(code, promise);
    } else {
      eventLoop.execute(() -> writeReset0(code, promise));
    }
    return promise.future();
  }

  protected void writeReset0(long code, Promise<Void> promise) {
    if (reset != -1L) {
      promise.fail("Stream already reset");
      return;
    }
    reset = code;
    int streamId;
    synchronized (this) {
      streamId = this.id;
    }
    if (streamId != -1) {
      conn.writeReset(id, code, promise);
    } else {
      // Reset happening before stream allocation
      handleReset(code);
    }
    promise.complete();
  }

  final void handleWriteQueueDrained() {
    Http2StreamHandler i = handler();
    if (i != null) {
      i.handleDrained();
    }
  }

  final void handleData(Buffer buf) {
    Http2StreamHandler i = handler();
    if (i != null) {
      i.handleData(buf);
    }
  }

  final void handleCustomFrame(HttpFrame frame) {
    Http2StreamHandler i = handler();
    if (i != null) {
      i.handleCustomFrame(frame);
    }
  }

  void handleEnd(MultiMap trailers) {
    Http2StreamHandler i = handler();
    if (i != null) {
      i.handleEnd(trailers);
    }
  }

  final void handleReset(long errorCode) {
    Http2StreamHandler i = handler();
    if (i != null) {
      i.handleReset(errorCode);
    }
  }

  public final void handleException(Throwable cause) {
    Http2StreamHandler i = handler();
    if (i != null) {
      i.handleException(cause);
    }
  }

  final void handleClose() {
    Http2StreamHandler i = handler();
    if (i != null) {
      i.handleClose();
    }
  }

  final void handlePriorityChange(StreamPriority newPriority) {
    Http2StreamHandler i = handler();
    if (i != null) {
      i.handlePriorityChange(newPriority);
    }
  }

  public void priority(StreamPriority streamPriority) {
    this.priority = streamPriority;
  }

  public StreamPriority priority() {
    return priority;
  }

  public void updatePriority(StreamPriority priority) {
    if (!this.priority.equals(priority)) {
      this.priority = priority;
      if (id >= 0) {
        conn.writePriorityFrame(id, priority);
      }
    }
  }
}
