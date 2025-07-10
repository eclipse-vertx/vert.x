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

package io.vertx.core.http.impl.http3;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoop;
import io.netty.handler.stream.ChunkedInput;
import io.netty.incubator.codec.http3.DefaultHttp3Headers;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.http.impl.HttpFrameImpl;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.http.impl.headers.VertxHttpHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.concurrent.InboundMessageQueue;
import io.vertx.core.internal.concurrent.OutboundMessageQueue;
import io.vertx.core.net.impl.MessageWrite;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public abstract class Http3StreamBase {

  private static final Http3HeadersMultiMap EMPTY = new Http3HeadersMultiMap(new DefaultHttp3Headers());

  private final OutboundMessageQueue<MessageWrite> outboundQueue;
  private final InboundMessageQueue<Object> inboundQueue;
  private final Http3Connection connection;
  protected final VertxInternal vertx;
  protected final ContextInternal context;
  protected QuicStreamChannel streamChannel;


  // Accessed from event-loop
  private boolean headersReceived;
  private boolean trailersReceived;
  private boolean headersSent;
  private boolean trailersSent;
  private boolean writable;

  // Client context
  private StreamPriorityBase priority;
  private long bytesRead;
  private long bytesWritten;
  private Throwable failure;
  private long reset = -1L;
  private boolean first_ = true;

//  private int headerReceivedCount = 0;


/*
  protected abstract void consumeCredits(S stream, int len);

  protected abstract void writeFrame(S stream, byte type, short flags, ByteBuf payload, Promise<Void> promise);

  protected abstract void writeHeaders(S stream, VertxHttpHeaders headers, boolean end, StreamPriorityBase priority,
                                       boolean checkFlush, FutureListener<Void> promise);

  protected abstract void writePriorityFrame(StreamPriorityBase priority);

  protected abstract void writeData_(S stream, ByteBuf chunk, boolean end, FutureListener<Void> promise);

  protected abstract void writeReset_(int streamId, long code, FutureListener<Void> listener);

  protected abstract void init_(VertxHttpStreamBase vertxHttpStream, S stream);

  protected abstract int getStreamId();

  protected abstract boolean remoteSideOpen(S stream);

  protected abstract MultiMap getEmptyHeaders();

  protected abstract boolean evaluateChannelWritability(S streamChannel);
*/

  protected abstract StreamPriorityBase createDefaultStreamPriority();

  Http3StreamBase(Http3Connection connection, ContextInternal context) {
    this(-1, connection, context, true);
  }

  Http3StreamBase(int id_, Http3Connection connection, ContextInternal context, boolean writable) {
    this.connection = connection;
    this.vertx = context.owner();
    this.context = context;
//    this.id = id_;
    this.inboundQueue = new InboundMessageQueue<>(connection.context().eventLoop(), context.executor()) {
      @Override
      protected void handleMessage(Object item) {
        if (item instanceof MultiMap) {
          handleTrailers((MultiMap) item);
        } else {
          Buffer data = (Buffer) item;
          int len = data.length();
          connection.context().execute(len, v -> connection.consumeCredits(Http3StreamBase.this.streamChannel, v));
          handleData(data);
        }
      }
    };
    this.priority = createDefaultStreamPriority();
    this.writable = writable;
    this.outboundQueue = new OutboundMessageQueue<>(connection.context().executor()) {
      // TODO implement stop drain to optimize flushes ?
      @Override
      public boolean test(MessageWrite msg) {
        if (Http3StreamBase.this.writable) {
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
        context.execute(Http3StreamBase.this, Http3StreamBase::handleWriteQueueDrained);
      }
    };
    if (streamChannel != null) {
      // Not great but well
      if (this instanceof Http3ClientStream) {
        this.headersSent = true;
        this.trailersSent = true;
      } else {
        this.headersReceived = true;
        this.trailersReceived = true;
      }
    }
  }

  public abstract Http3Connection connection();

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

  public void priority(StreamPriorityBase streamPriority) {
    this.priority = streamPriority;
  }

  public StreamPriorityBase priority() {
    return priority;
  }

  // Should use generic for Http3StreamHandler
  public abstract Http3StreamHandler handler();

  public void init(QuicStreamChannel streamChannel, boolean writable) {
    this.streamChannel = streamChannel;
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

  public void onHeaders(Http3HeadersMultiMap headers) {
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

  public void onPriorityChange(StreamPriorityBase newPriority) {
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

  public final void onTrailers(Http3HeadersMultiMap trailers) {
    if (trailersReceived) {
      throw new IllegalStateException();
    }
    trailersReceived = true;
    observeInboundTrailers();
    connection.flushBytesRead();
    inboundQueue.write(trailers);
  }

  public final int id() {
    return Math.toIntExact(streamChannel.streamId());
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

  public final void pause() {
    inboundQueue.pause();
  }

  public final void fetch(long amount) {
    inboundQueue.fetch(amount);
  }

  public final Future<Void> writeFrame(int type, int flags, ByteBuf payload) {
    Promise<Void> promise = context.promise();
    EventLoop eventLoop = connection.context().nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      connection.writeFrame(streamChannel, type, flags, payload, promise);
    } else {
      eventLoop.execute(() -> connection.writeFrame(streamChannel, type, flags, payload, promise));
    }
    return promise.future();
  }

  public final void writeHeaders(Http3HeadersMultiMap headers, boolean end, boolean checkFlush, Promise<Void> promise) {
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

  void writeHeaders0(Http3HeadersMultiMap headers, boolean end, boolean checkFlush, Promise<Void> promise) {
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
    connection.writeHeaders(streamChannel, headers, priority, end, checkFlush, promise);
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
    connection.sendFile(streamChannel, file, promise);
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
    connection.writeData(streamChannel, chunk, end, promise);
  }

  public final Future<Void> writeReset(long code) {
    if (code < 0L) {
      throw new IllegalArgumentException("Invalid reset code value");
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
        if (streamChannel != null) {
          connection.writeReset(streamChannel, code, promise);
        } else {
          // Reset happening before stream allocation
          handleReset(code);
          promise.complete();
        }
      }
    }
  }

  private void handleWriteQueueDrained() {
    Http3StreamHandler i = handler();
    if (i != null) {
      i.handleDrained();
    }
  }

  private void handleData(Buffer buf) {
    Http3StreamHandler i = handler();
    if (i != null) {
      i.handleData(buf);
    }
  }

  private void handleCustomFrame(HttpFrame frame) {
    Http3StreamHandler i = handler();
    if (i != null) {
      i.handleCustomFrame(frame);
    }
  }

  private void handleTrailers(MultiMap trailers) {
    Http3StreamHandler i = handler();
    if (i != null) {
      i.handleTrailers(trailers);
    }
  }

  private void handleReset(long errorCode) {
    Http3StreamHandler i = handler();
    if (i != null) {
      i.handleReset(errorCode);
    }
  }

  public final void handleException(Throwable cause) {
    Http3StreamHandler i = handler();
    if (i != null) {
      i.handleException(cause);
    }
  }

  private void handleHeader(Http3HeadersMultiMap map) {
    Http3StreamHandler handler = handler();
    if (handler != null) {
      handler.handleHeaders(map);
    }
  }

  private void handleClose() {
    Http3StreamHandler i = handler();
    if (i != null) {
      i.handleClose();
    }
  }

  private void handlePriorityChange(StreamPriorityBase newPriority) {
    Http3StreamHandler i = handler();
    if (i != null) {
      i.handlePriorityChange(newPriority);
    }
  }

  public void updatePriority(StreamPriorityBase priority) {
    if (!this.priority.equals(priority)) {
      this.priority = priority;
      if (streamChannel != null) {
        connection.writePriorityFrame(streamChannel, priority);
      }
    }
  }

  protected void observeReset() {
  }

  protected void observeInboundHeaders(Http3HeadersMultiMap headers) {
  }

  protected void observeOutboundTrailers() {
  }

  protected void observeOutboundHeaders(Http3HeadersMultiMap headers) {
  }

  protected void observeInboundTrailers() {
  }

  public long getReset() {
    return reset;
  }

  public void determineIfTrailersReceived(VertxHttpHeaders headers) {
    trailersReceived = headersReceived && headers.method() == null && headers.status() == null;
//    headerReceivedCount++;
  }

  public void init(QuicStreamChannel streamChannel) {
    synchronized (this) {
      this.streamChannel = streamChannel;
    }
    this.writable = this.evaluateChannelWritability(streamChannel);
  }

  protected boolean evaluateChannelWritability(QuicStreamChannel streamChannel) {
    return streamChannel.isWritable();
  }

}
