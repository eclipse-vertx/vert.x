package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.http.impl.headers.VertxHttpHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.net.impl.MessageWrite;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.streams.WriteStream;
import io.vertx.core.tracing.TracingPolicy;

import java.util.Map;
import java.util.function.BiConsumer;

abstract class HttpStreamImpl<C extends ConnectionBase, S> extends HttpStream<C, S> implements HttpClientStream {

  private Throwable reset;

  protected abstract boolean isTryUseCompression();

  abstract int lastStreamCreated();

  protected abstract Future<S> createStreamChannelInternal(int id, boolean b) throws HttpException;

  protected abstract TracingPolicy getTracingPolicy();

  abstract VertxHttpHeaders createHttpHeadersWrapper();

  HttpStreamImpl(C conn, ContextInternal context, boolean push) {
    super(conn, context, push);
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
  public HttpStreamImpl<?, ?> drainHandler(Handler<Void> handler) {
    drainHandler = handler;
    return this;
  }

  @Override
  public HttpStreamImpl<?, ?> exceptionHandler(Handler<Throwable> handler) {
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
    return !isWritable();
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
  public void priorityHandler(Handler<StreamPriorityBase> handler) {
    priorityHandler = handler;
  }

  @Override
  public void endHandler(Handler<MultiMap> handler) {
    endHandler = handler;
  }

  @Override
  public StreamPriorityBase priority() {
    return super.priority();
  }

  @Override
  public void updatePriority(StreamPriorityBase streamPriority) {
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
  void handleWriteQueueDrained() {
    Handler<Void> handler = drainHandler;
    if (handler != null) {
      context.dispatch(null, handler);
    }
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
  void handlePriorityChange(StreamPriorityBase streamPriority) {
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
  public Future<Void> writeHead(HttpRequestHead request, boolean chunked, ByteBuf buf, boolean end,
                                StreamPriorityBase priority, boolean connect) {
    priority(priority);
    PromiseInternal<Void> promise = context.promise();
    write(new MessageWrite() {
      @Override
      public void write() {
        writeHeaders(request, buf, end, priority, connect, promise);
      }

      @Override
      public void cancel(Throwable cause) {
        promise.fail(cause);
      }
    });
    return promise.future();
  }

  private void writeHeaders(HttpRequestHead request, ByteBuf buf, boolean end, StreamPriorityBase priority,
                            boolean connect, Promise<Void> promise) {
    VertxHttpHeaders headers = createHttpHeadersWrapper();
    headers.method(request.method.name());
    boolean e;
    if (request.method == HttpMethod.CONNECT) {
      if (request.authority == null) {
        throw new IllegalArgumentException("Missing :authority / host header");
      }
      headers.authority(request.authority);
      // don't end stream for CONNECT
      e = false;
    } else {
      headers.path(request.uri);
      headers.scheme(conn.isSsl() ? "https" : "http");
      if (request.authority != null) {
        headers.authority(request.authority);
      }
      e = end;
    }
    if (request.headers != null && request.headers.size() > 0) {
      for (Map.Entry<String, String> header : request.headers) {
        headers.add(HttpUtils.toLowerCase(header.getKey()), header.getValue());
      }
    }
    //TODO: check with old impl: if (conn.client.options().isDecompressionSupported() && headers.get(HttpHeaderNames
    // .ACCEPT_ENCODING) == null) {
    if (isTryUseCompression() && headers.get(HttpHeaderNames.ACCEPT_ENCODING) == null) {
      headers.set(HttpHeaderNames.ACCEPT_ENCODING, Http1xClientConnection.determineCompressionAcceptEncoding());
    }
    try {
      createStream(request, headers, streamChannel_ -> {
        if (buf != null) {
          doWriteHeaders(headers, false, false, null);
          doWriteData(buf, e, promise);
        } else {
          doWriteHeaders(headers, e, true, promise);
        }
      });
    } catch (HttpException ex) {
      promise.fail(ex);
      onException(ex);
    }
  }

  private void createStream(HttpRequestHead head, VertxHttpHeaders headers, Handler<S> onComplete) throws HttpException {
    int id = lastStreamCreated();
    if (id == 0) {
      id = 1;
    } else {
      id += 2;
    }
    head.id = id;
    head.remoteAddress = conn.remoteAddress();
    createStreamChannelInternal(id, false).onSuccess(streamChannel -> {
      init(streamChannel);
      if (metrics() != null) {
        metric = metrics().requestBegin(headers.path().toString(), head);
      }
      VertxTracer tracer = context.tracer();
      if (tracer != null) {
        BiConsumer<String, String> headers_ = (key, val) -> headers.add(key, val);
        String operation = head.traceOperation;
        if (operation == null) {
          operation = headers.method().toString();
        }
        //TODO: verify the following line with version 5.x: trace = tracer.sendRequest(context, SpanKind.RPC, conn
        // .client.options().getTracingPolicy(), head, operation, headers_, HttpUtils
        // .CLIENT_HTTP_REQUEST_TAG_EXTRACTOR);
        trace = tracer.sendRequest(context, SpanKind.RPC, getTracingPolicy(), head, operation, headers_,
          HttpUtils.CLIENT_HTTP_REQUEST_TAG_EXTRACTOR);
      }
      onComplete.handle(streamChannel);
    }).onFailure(this::handleException);
  }

  @Override
  public Future<Void> writeBuffer(ByteBuf buf, boolean end) {
    Promise<Void> promise = context.promise();
    writeData(buf, end, promise);
    return promise.future();


    //TODO: the following codes are commented from 4.x
/*
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
*/
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
    reset = cause;
    long code;
    if (cause instanceof StreamResetException) {
      code = ((StreamResetException) cause).getCode();
    } else if (cause instanceof java.util.concurrent.TimeoutException) {
      code = 0x08L; // CANCEL
    } else {
      code = 0L;
    }
    return writeReset(code);
  }

  @Override
  public HttpClientConnectionInternal connection() {
    return (HttpClientConnectionInternal) conn;
  }

  @Override
  protected Throwable getResetException() {
    return reset;
  }
}
