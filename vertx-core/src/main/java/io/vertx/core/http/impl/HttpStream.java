package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriorityBase;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.http.impl.headers.VertxHttpHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.Map;

abstract class HttpStream<C extends ConnectionBase, S> extends VertxHttpStreamBase<C, S> {

  private final boolean push;
  private HttpResponseHead response;
  protected Object metric;
  protected Object trace;
  protected boolean requestEnded;
  private boolean responseEnded;
  protected Handler<HttpResponseHead> headHandler;
  protected Handler<Buffer> chunkHandler;
  protected Handler<MultiMap> endHandler;
  protected Handler<StreamPriorityBase> priorityHandler;
  protected Handler<Void> drainHandler;
  protected Handler<Void> continueHandler;
  protected Handler<MultiMap> earlyHintsHandler;
  protected Handler<HttpFrame> unknownFrameHandler;
  protected Handler<Throwable> exceptionHandler;
  protected Handler<HttpClientPush> pushHandler;
  protected Handler<Void> closeHandler;
  protected long writeWindow;//todo: this prop is removed in 5.x
  protected final long windowSize;//todo: this prop is removed in 5.x

  protected abstract long getWindowSize();
  protected abstract HttpVersion version();
  protected abstract void recycle();
  protected abstract void metricsEnd(HttpStream<?, ?> stream);

  HttpStream(C conn, ContextInternal context, boolean push) {
    super(conn, context);

    this.push = push;
    this.windowSize = getWindowSize();
  }

  void onContinue() {
    context.emit(null, v -> handleContinue());
  }

  void onEarlyHints(MultiMap headers) {
    context.emit(null, v -> handleEarlyHints(headers));
  }

  abstract void handleContinue();

  abstract void handleEarlyHints(MultiMap headers);

  public Object metric() {
    return metric;
  }

  public Object trace() {
    return trace;
  }

  @Override
  void doWriteData(ByteBuf chunk, boolean end, Promise<Void> promise) {
    super.doWriteData(chunk, end, promise);
  }

  @Override
  void doWriteHeaders(VertxHttpHeaders headers, boolean end, boolean checkFlush, Promise<Void> promise) {
    isConnect = "CONNECT".contentEquals(headers.method());
    super.doWriteHeaders(headers, end, checkFlush, promise);
  }

  @Override
  protected void doWriteReset(long code) {
    if (!requestEnded || !responseEnded) {
      super.doWriteReset(code);
    }
  }

  protected void endWritten() {
    requestEnded = true;
    if (conn.metrics() != null) {
      metrics().requestEnd(metric, bytesWritten());
    }
  }

  protected ClientMetrics metrics() {
    return (ClientMetrics) conn.metrics();
  }

  @Override
  void onEnd(MultiMap trailers) {
    metricsEnd(this);
    responseEnded = true;
    super.onEnd(trailers);
  }

  @Override
  void onReset(long code) {
    if (metrics() != null) {
      metrics().requestReset(metric);
    }
    super.onReset(code);
  }

  @Override
  void onHeaders(VertxHttpHeaders headers, StreamPriorityBase streamPriority) {
    if (streamPriority != null) {
      priority(streamPriority);
    }
    if (response == null) {
      int status;
      String statusMessage;
      try {
        status = Integer.parseInt(String.valueOf(headers.status()));
        statusMessage = HttpResponseStatus.valueOf(status).reasonPhrase();
      } catch (Exception e) {
        handleException(e);
        writeReset(0x01 /* PROTOCOL_ERROR */);
        return;
      }
      if (status == 100) {
        onContinue();
        return;
      } else if (status == 103) {
        MultiMap headersMultiMap = HeadersMultiMap.httpHeaders();
        removeStatusHeaders(headers);
        for (Map.Entry<CharSequence, CharSequence> header : headers.getIterable()) {
          headersMultiMap.add(header.getKey(), header.getValue());
        }
        onEarlyHints(headersMultiMap);
        return;
      }
      response = new HttpResponseHead(
        version(),
        status,
        statusMessage,
        headers.toHeaderAdapter());
      removeStatusHeaders(headers);

      if (metrics() != null) {
        metrics().responseBegin(metric, response);
      }

      if (headHandler != null) {
        context.emit(response, headHandler);
      }
    }
  }

  private void removeStatusHeaders(VertxHttpHeaders headers) {
    headers.remove(HttpHeaders.PSEUDO_STATUS);
  }

  @Override
  void onClose() {
    if (metrics() != null) {
      if (!requestEnded || !responseEnded) {
        metrics().requestReset(metric);
      }
    }
    VertxTracer tracer = context.tracer();
    if (tracer != null && trace != null) {
      VertxException err;
      if (responseEnded && requestEnded) {
        err = null;
      } else {
        err = HttpUtils.STREAM_CLOSED_EXCEPTION;
      }
      tracer.receiveResponse(context, response, trace, err, HttpUtils.CLIENT_RESPONSE_TAG_EXTRACTOR);
    }
    if (!responseEnded) {
      // NOT SURE OF THAT
      onException(HttpUtils.STREAM_CLOSED_EXCEPTION);
    }
    super.onClose();
    // commented to be used later when we properly define the HTTP/2 connection expiration from the pool
    // boolean disposable = conn.streams.isEmpty();
    if (!push) {
      recycle();
    } /* else {
        conn.listener.onRecycle(0, disposable);
      } */
    if (closeHandler != null) {
      closeHandler.handle(null);
    }
  }
}
