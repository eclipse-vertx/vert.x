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
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.headers.Http2HeadersAdaptor;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.concurrent.InboundMessageQueue;
import io.vertx.core.internal.concurrent.OutboundMessageQueue;
import io.vertx.core.net.impl.MessageWrite;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class VertxHttp2Stream<C extends Http2ConnectionBase> {

  private static final MultiMap EMPTY = new Http2HeadersAdaptor(EmptyHttp2Headers.INSTANCE);

  private final OutboundMessageQueue<MessageWrite> outboundQueue;
  private final InboundMessageQueue<Object> inboundQueue;
  protected final C conn;
  protected final VertxInternal vertx;
  protected final ContextInternal context;
  protected Http2Stream stream;

  // Client context
  private boolean writable;
  private StreamPriority priority;
  private long bytesRead;
  private long bytesWritten;
  protected boolean isConnect;
  private Throwable failure;

  VertxHttp2Stream(C conn, ContextInternal context) {
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
          conn.getContext().emit(null, v -> {
            if (stream.state().remoteSideOpen()) {
              // Handle the HTTP upgrade case
              // buffers are received by HTTP/1 and not accounted by HTTP/2
              conn.consumeCredits(stream, len);
            }
          });
          handleData(data);
        }
      }
    };
    this.priority = HttpUtils.DEFAULT_STREAM_PRIORITY;
    this.isConnect = false;
    this.writable = true;
    this.outboundQueue = new OutboundMessageQueue<>(conn.getContext().nettyEventLoop()) {
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
        context.emit(VertxHttp2Stream.this, VertxHttp2Stream::handleWriteQueueDrained);
      }
    };
  }

  void init(Http2Stream stream) {
    synchronized (this) {
      this.stream = stream;
    }
    writable = this.conn.handler.encoder().flowController().isWritable(stream);
    stream.setProperty(conn.streamKey, this);
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
    onEnd(EMPTY);
  }

  void onEnd(MultiMap trailers) {
    conn.flushBytesRead();
    inboundQueue.write(trailers);
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
    EventLoop eventLoop = conn.getContext().nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      doWriteFrame(type, flags, payload, promise);
    } else {
      eventLoop.execute(() -> doWriteFrame(type, flags, payload, promise));
    }
    return promise.future();
  }

  public final void writeFrame(int type, int flags, ByteBuf payload, Promise<Void> promise) {
    EventLoop eventLoop = conn.getContext().nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      doWriteFrame(type, flags, payload, promise);
    } else {
      eventLoop.execute(() -> doWriteFrame(type, flags, payload, promise));
    }
  }

  private void doWriteFrame(int type, int flags, ByteBuf payload, Promise<Void> promise) {
    conn.handler.writeFrame(stream, (byte) type, (short) flags, payload, (FutureListener<Void>) promise);
  }

  final void writeHeaders(Http2Headers headers, boolean first, boolean end, boolean checkFlush, Promise<Void> promise) {
    if (first) {
      EventLoop eventLoop = conn.getContext().nettyEventLoop();
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

  void doWriteHeaders(Http2Headers headers, boolean end, boolean checkFlush, Promise<Void> promise) {
    if (end) {
      endWritten();
    }
    conn.handler.writeHeaders(stream, headers, end, priority.getDependency(), priority.getWeight(), priority.isExclusive(), checkFlush, (FutureListener<Void>) promise);
  }

  protected void endWritten() {
  }

  private void writePriorityFrame(StreamPriority priority) {
    conn.handler.writePriority(stream, priority.getDependency(), priority.getWeight(), priority.isExclusive());
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
    conn.handler.writeData(stream, chunk, end, (FutureListener<Void>) promise);
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

  void handleWriteQueueDrained() {
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
