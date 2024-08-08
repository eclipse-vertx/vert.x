package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.Headers;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.streams.WriteStream;

abstract class HttpStreamImpl<C extends ConnectionBase, S,
  H extends Headers<CharSequence, CharSequence, H>> extends HttpStream<C, S, H> implements HttpClientStream {

  HttpStreamImpl(C conn, ContextInternal context, boolean push, VertxHttpConnectionDelegate<S, H> connectionDelegate,
                 ClientMetrics metrics) {
    super(conn, context, push, connectionDelegate, metrics);
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
  public HttpStreamImpl<?, ?, ?> drainHandler(Handler<Void> handler) {
    drainHandler = handler;
    return this;
  }

  @Override
  public HttpStreamImpl<?, ?, ?> exceptionHandler(Handler<Throwable> handler) {
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
  public synchronized boolean isNotWritable() {
    return writeWindow > windowSize;
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
  void handleWritabilityChanged(boolean writable) {
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

  void handleContinue() {
    if (continueHandler != null) {
      continueHandler.handle(null);
    }
  }

  void handleEarlyHints(MultiMap headers) {
    if (earlyHintsHandler != null) {
      earlyHintsHandler.handle(headers);
    }
  }

  void handleException(Throwable exception) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(exception);
    }
  }

  @Override
  public void writeHead(HttpRequestHead request, boolean chunked, ByteBuf buf, boolean end, StreamPriority priority,
                        boolean connect, Handler<AsyncResult<Void>> handler) {
    priority(priority);
    conn.context.emit(null, v -> {
      writeHeaders(request, buf, end, priority, connect, handler);
    });
  }

  protected abstract void writeHeaders(HttpRequestHead request, ByteBuf buf, boolean end, StreamPriority priority,
                                       boolean connect,
                                       Handler<AsyncResult<Void>> handler);

  @Override
  public void writeBuffer(ByteBuf buf, boolean end, Handler<AsyncResult<Void>> listener) {
    if (buf != null) {
      int size = buf.readableBytes();
      synchronized (this) {
        writeWindow += size;
      }
      if (listener != null) {
        Handler<AsyncResult<Void>> prev = listener;
        listener = ar -> {
          Handler<Void> drainHandler;
          synchronized (this) {
            boolean full = writeWindow > windowSize;
            writeWindow -= size;
            if (full && writeWindow <= windowSize) {
              drainHandler = this.drainHandler;
            } else {
              drainHandler = null;
            }
          }
          if (drainHandler != null) {
            drainHandler.handle(null);
          }
          prev.handle(ar);
        };
      }
    }
    writeData(buf, end, listener);
  }

  @Override
  public ContextInternal getContext() {
    return context;
  }

  @Override
  public void doSetWriteQueueMaxSize(int size) {
  }

  @Override
  public void reset(Throwable cause) {
    long code = cause instanceof StreamResetException ? ((StreamResetException) cause).getCode() : 0;
    conn.context.emit(code, this::writeReset);
  }

}
