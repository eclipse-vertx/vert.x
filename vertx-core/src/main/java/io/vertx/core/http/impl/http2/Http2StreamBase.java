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
import io.netty.handler.stream.ChunkedInput;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.impl.HttpFrameImpl;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.concurrent.InboundMessageQueue;
import io.vertx.core.internal.concurrent.OutboundMessageQueue;
import io.vertx.core.net.impl.MessageWrite;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class Http2StreamBase<S extends Http2StreamBase<S>> {

  private static final Http2HeadersMultiMap EMPTY = new Http2HeadersMultiMap(EmptyHttp2Headers.INSTANCE);

  private final OutboundMessageQueue<MessageWrite> outboundQueue;
  private final InboundMessageQueue<Object> inboundQueue;
  private final Http2Connection connection;
  protected final VertxInternal vertx;
  protected final ContextInternal context;
  private int id;

  // Accessed from event-loop
  private boolean headersReceived;
  private boolean trailersReceived;
  private boolean headersSent;
  private boolean trailersSent;
  private boolean writable;

  // Client context
  private StreamPriority priority;
  private long bytesRead;
  private long bytesWritten;
  private Throwable failure;
  private long reset = -1L;
  private boolean first_ = true;

  // Handlers
  private Handler<Long> resetHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> closeHandler;
  private Handler<Buffer> dataHandler;
  private Handler<MultiMap> trailersHandler;
  private Handler<HttpFrame> customFrameHandler;
  private Handler<StreamPriority> priorityChangeHandler;
  private Handler<Void> drainHandler;

  Http2StreamBase(Http2Connection connection, ContextInternal context) {
    this(-1, connection, context, true);
  }

  Http2StreamBase(int id_, Http2Connection connection, ContextInternal context, boolean writable) {
    this.connection = connection;
    this.vertx = context.owner();
    this.context = context;
    this.id = id_;
    this.inboundQueue = new InboundMessageQueue<>(connection.context().eventLoop(), context.executor()) {
      @Override
      protected void handleMessage(Object item) {
        if (item instanceof MultiMap) {
          handleTrailers((MultiMap) item);
        } else {
          Buffer data = (Buffer) item;
          int len = data.length();
          connection.context().execute(len, v -> connection.consumeCredits(Http2StreamBase.this.id, v));
          handleData(data);
        }
      }
    };
    this.priority = HttpUtils.DEFAULT_STREAM_PRIORITY;
    this.writable = writable;
    this.outboundQueue = new OutboundMessageQueue<>(connection.context().executor()) {
      // TODO implement stop drain to optimize flushes ?
      @Override
      public boolean test(MessageWrite msg) {
        if (Http2StreamBase.this.writable) {
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
        context.execute(Http2StreamBase.this, Http2StreamBase::handleWriteQueueDrained);
      }
    };
    if (id >= 0) {
      // Not great but well
      if (this instanceof Http2ClientStream) {
        this.headersSent = true;
        this.trailersSent = true;
      } else {
        this.headersReceived = true;
        this.trailersReceived = true;
      }
    }
  }

  public abstract Http2Connection connection();

  public final boolean isHeadersReceived() {
    return headersReceived;
  }

  public final boolean isTrailersReceived() {
    return trailersReceived;
  }

  public final boolean isHeadersSent() {
    return headersSent;
  }

  public final boolean isTrailersSent() {
    return trailersSent;
  }

  public final ContextInternal context() {
    return context;
  }

  public void priority(StreamPriority streamPriority) {
    this.priority = streamPriority;
  }

  public StreamPriority priority() {
    return priority;
  }

  public void init(int streamId, boolean writable) {
    assert id < 0;
    this.id = streamId;
    this.writable = writable;
  }

  public void onClose() {
    if (!trailersSent || !trailersReceived) {
      observeReset();
    }
    connection.flushBytesWritten();
    context.execute(ex -> handleClose());
    outboundQueue.close();
  }

  public void onReset(long code) {
    observeReset();
    reset = code;
    context.execute(code, this::handleReset);
  }

  public void onHeaders(Http2HeadersMultiMap headers) {
    if (headersReceived) {
      throw new IllegalStateException();
    }
    headersReceived = true;
    observeInboundHeaders(headers);
    context.execute(headers, this::handleHeader);
  }

  public void onException(Throwable cause) {
    failure = cause;
    context.execute(cause, this::handleException);
  }

  public void onPriorityChange(StreamPriority newPriority) {
    if (!priority.equals(newPriority)) {
      priority = newPriority;
      context.execute(newPriority, this::handlePriorityChange);
    }
  }

  public void onCustomFrame(int type, int flags, Buffer payload) {
    context.execute(new HttpFrameImpl(type, flags, payload), this::handleCustomFrame);
  }

  public void onData(Buffer data) {
    bytesRead += data.length();
    connection.reportBytesRead(data.length());
    inboundQueue.write(data);
  }

  public void onWritabilityChanged() {
    writable = !writable;
    if (writable) {
      outboundQueue.tryDrain();
    }
  }

  public final void onTrailers() {
    onTrailers(EMPTY);
  }

  public final void onTrailers(Http2HeadersMultiMap trailers) {
    if (trailersReceived) {
      throw new IllegalStateException();
    }
    trailersReceived = true;
    observeInboundTrailers();
    connection.flushBytesRead();
    inboundQueue.write(trailers);
  }

  public final int id() {
    return id;
  }

  public final long bytesWritten() {
    return bytesWritten;
  }

  public final long bytesRead() {
    return bytesRead;
  }

  public final boolean isWritable() {
    return outboundQueue.isWritable();
  }

  public final void write(MessageWrite write) {
    outboundQueue.write(write);
  }

  public final S pause() {
    inboundQueue.pause();
    return (S)this;
  }

  public final S fetch(long amount) {
    inboundQueue.fetch(amount);
    return (S)this;
  }

  public final Future<Void> writeFrame(int type, int flags, Buffer payload) {
    Promise<Void> promise = context.promise();
    EventLoop eventLoop = connection.context().nettyEventLoop();
    ByteBuf byteBuf = ((BufferInternal) payload).getByteBuf();
    if (eventLoop.inEventLoop()) {
      connection.writeFrame(id, type, flags, byteBuf, promise);
    } else {
      eventLoop.execute(() -> connection.writeFrame(id, type, flags, byteBuf, promise));
    }
    return promise.future();
  }

  public final void writeHeaders(Http2HeadersMultiMap headers, boolean end, boolean checkFlush, Promise<Void> promise) {
    if (first_) {
      first_ = false;
      EventLoop eventLoop = connection.context().nettyEventLoop();
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
    if (!headersSent) {
      headersSent = true;
      observeOutboundHeaders(headers);
    }
    if (end) {
      trailersSent = true;
      observeOutboundTrailers();
    }
    connection.writeHeaders(id, headers, priority, end, checkFlush, promise);
  }

  public final void sendFile(ChunkedInput<ByteBuf> file, Promise<Void> promise) {
    bytesWritten += file.length();
    outboundQueue.write(new MessageWrite() {
      @Override
      public void write() {
        sendFile0(file, promise);
      }
      @Override
      public void cancel(Throwable cause) {
        promise.fail(cause);
      }
    });
  }

  private void sendFile0(ChunkedInput<ByteBuf> file, Promise<Void> promise) {
    connection.sendFile(id, file, promise);
  }

  public final void writeData(ByteBuf chunk, boolean end, Promise<Void> promise) {
    write(new MessageWrite() {
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
    connection.reportBytesWritten(numOfBytes);
    if (end) {
      trailersSent = true;
      observeOutboundTrailers();
    }
    connection.writeData(id, chunk, end, promise);
  }

  public final Future<Void> writeReset(long code) {
    if (code < 0L) {
      throw new IllegalArgumentException("Invalid reset code value");
    }
    if (id < 0) {
      // Not yet sent hack : todo improve this
      return null;
    }
    Promise<Void> promise = context.promise();
    EventLoop eventLoop = connection.context().nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      writeReset0(code, promise);
    } else {
      eventLoop.execute(() -> writeReset0(code, promise));
    }
    return promise.future();
  }

  private void writeReset0(long code, Promise<Void> promise) {
    if (trailersSent && trailersReceived) {
      promise.fail("Request ended");
    } else {
      if (reset != -1L) {
        promise.fail("Stream already reset");
      } else {
        reset = code;
        if (id != -1) {
          connection.writeReset(id, code, promise);
        } else {
          // Reset happening before stream allocation
          handleReset(code);
          promise.complete();
        }
      }
    }
  }

  public S drainHandler(Handler<Void> handler) {
    drainHandler = handler;
    return (S)this;
  }

  private void handleWriteQueueDrained() {
    Handler<Void> i = drainHandler;
    if (i != null) {
      context.dispatch(null, i);
    }
  }

  public S dataHandler(Handler<Buffer> handler) {
    dataHandler = handler;
    return (S)this;
  }

  private void handleData(Buffer buf) {
    Handler<Buffer> handler = dataHandler;
    if (handler != null) {
      context.emit(buf, handler);
    }
  }

  public S customFrameHandler(Handler<HttpFrame> handler) {
    customFrameHandler = handler;
    return (S)this;
  }

  private void handleCustomFrame(HttpFrame frame) {
    Handler<HttpFrame> handler = customFrameHandler;
    if (handler != null) {
      context.emit(frame, handler);
    }
  }

  public S trailersHandler(Handler<MultiMap> handler) {
    trailersHandler = handler;
    return (S)this;
  }

  private void handleTrailers(MultiMap trailers) {
    Handler<MultiMap> handler = trailersHandler;
    if (handler != null) {
      context.emit(trailers, handler);
    }
  }

  public S resetHandler(Handler<Long> handler) {
    resetHandler = handler;
    return (S)this;
  }

  private void handleReset(long errorCode) {
    Handler<Long> handler = resetHandler;
    if (handler != null) {
      context.emit(errorCode, handler);
    }
  }

  public S exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return (S)this;
  }

  public final void handleException(Throwable cause) {
    Handler<Throwable> handler = exceptionHandler;
    if (handler != null) {
      context.emit(cause, handler);
    }
  }

  abstract void handleHeader(Http2HeadersMultiMap map);

  public S closeHandler(Handler<Void> handler) {
    closeHandler = handler;
    return (S)this;
  }

  private void handleClose() {
    Handler<Void> handler = closeHandler;
    if (handler != null) {
      context.emit(null, handler);
    }
  }

  public S priorityChangeHandler(Handler<StreamPriority> handler) {
    priorityChangeHandler = handler;
    return (S)this;
  }

  private void handlePriorityChange(StreamPriority newPriority) {
    Handler<StreamPriority> handler = priorityChangeHandler;
    if (handler != null) {
      context.emit(newPriority, handler);
    }
  }

  public final S updatePriority(StreamPriority priority) {
    if (!this.priority.equals(priority)) {
      this.priority = priority;
      if (id >= 0) {
        connection.writePriorityFrame(id, priority);
      }
    }
    return (S)this;
  }

  protected void observeReset() {
  }

  protected void observeInboundHeaders(Http2HeadersMultiMap headers) {
  }

  protected void observeOutboundTrailers() {
  }

  protected void observeOutboundHeaders(Http2HeadersMultiMap headers) {
  }

  protected void observeInboundTrailers() {
  }
}
