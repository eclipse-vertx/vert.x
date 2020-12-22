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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.headers.Http2HeadersAdaptor;
import io.vertx.core.impl.EventLoopContext;
import io.vertx.core.net.impl.clientconnection.ConnectionListener;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.*;
import java.util.function.BiConsumer;

import static io.vertx.core.http.HttpHeaders.DEFLATE_GZIP;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http2ClientConnection extends Http2ConnectionBase implements HttpClientConnection {

  private final HttpClientImpl client;
  private final ClientMetrics metrics;
  private Handler<Boolean> lifecycleHandler = DEFAULT_LIFECYCLE_HANDLER;
  private Handler<Long> concurrencyChangeHandler = DEFAULT_CONCURRENCY_CHANGE_HANDLER;
  private long expirationTimestamp;
  private boolean evicted;

  Http2ClientConnection(HttpClientImpl client,
                        EventLoopContext context,
                        VertxHttp2ConnectionHandler connHandler,
                        ClientMetrics metrics) {
    super(context, connHandler);
    this.metrics = metrics;
    this.client = client;
  }

  @Override
  public Http2ClientConnection lifecycleHandler(Handler<Boolean> handler) {
    lifecycleHandler = handler;
    return this;
  }

  @Override
  public Http2ClientConnection concurrencyChangeHandler(Handler<Long> handler) {
    concurrencyChangeHandler = handler;
    return this;
  }

  public long concurrency() {
    long concurrency = remoteSettings().getMaxConcurrentStreams();
    long http2MaxConcurrency = client.getOptions().getHttp2MultiplexingLimit() <= 0 ? Long.MAX_VALUE : client.getOptions().getHttp2MultiplexingLimit();
    if (http2MaxConcurrency > 0) {
      concurrency = Math.min(concurrency, http2MaxConcurrency);
    }
    return concurrency;
  }

  @Override
  synchronized boolean onGoAwaySent(int lastStreamId, long errorCode, ByteBuf debugData) {
    boolean goneAway = super.onGoAwaySent(lastStreamId, errorCode, debugData);
    if (goneAway) {
      // Eagerly evict from the pool
      tryEvict();
    }
    return goneAway;
  }

  @Override
  synchronized boolean onGoAwayReceived(int lastStreamId, long errorCode, ByteBuf debugData) {
    boolean goneAway = super.onGoAwayReceived(lastStreamId, errorCode, debugData);
    if (goneAway) {
      // Eagerly evict from the pool
      tryEvict();
    }
    return goneAway;
  }

  /**
   * Try to evict the connection from the pool. This can be called multiple times since
   * the connection can be eagerly removed from the pool on emission or reception of a {@code GOAWAY}
   * frame.
   */
  private void tryEvict() {
    if (!evicted) {
      evicted = true;
      lifecycleHandler.handle(false);
    }
  }

  @Override
  protected void concurrencyChanged(long concurrency) {
    int limit = client.getOptions().getHttp2MultiplexingLimit();
    if (limit > 0) {
      concurrency = Math.min(concurrency, limit);
    }
    concurrencyChangeHandler.handle(concurrency);
  }

  @Override
  public HttpClientMetrics metrics() {
    return client.metrics();
  }

  void upgradeStream(Object metric, ContextInternal context, Handler<AsyncResult<HttpClientStream>> completionHandler) {
    Future<HttpClientStream> fut;
    synchronized (this) {
      try {
        StreamImpl stream = createStream(context);
        stream.init(handler.connection().stream(1));
        ((Stream)stream).metric = metric;
        fut = Future.succeededFuture(stream);
      } catch (Exception e) {
        fut = Future.failedFuture(e);
      }
    }
    completionHandler.handle(fut);
  }

  @Override
  public void createStream(ContextInternal context, Handler<AsyncResult<HttpClientStream>> handler) {
    Future<HttpClientStream> fut;
    synchronized (this) {
      try {
        StreamImpl stream = createStream(context);
        fut = Future.succeededFuture(stream);
      } catch (Exception e) {
        fut = Future.failedFuture(e);
      }
    }
    context.emit(fut, handler);
  }

  private StreamImpl createStream(ContextInternal context) {
    return new StreamImpl(this, context, false);
  }

  private void recycle() {
    int timeout = client.getOptions().getHttp2KeepAliveTimeout();
    long expired = timeout > 0 ? System.currentTimeMillis() + timeout * 1000 : 0L;
    expirationTimestamp = timeout > 0 ? System.currentTimeMillis() + timeout * 1000 : 0L;
    lifecycleHandler.handle(true);
  }

  @Override
  public boolean isValid() {
    return expirationTimestamp == 0 || System.currentTimeMillis() <= expirationTimestamp;
  }

  protected synchronized void onHeadersRead(int streamId, Http2Headers headers, StreamPriority streamPriority, boolean endOfStream) {
    Stream stream = (Stream) stream(streamId);
    if (!stream.stream.isTrailersReceived()) {
      stream.onHeaders(headers, streamPriority);
      if (endOfStream) {
        stream.onEnd();
      }
    } else {
      stream.onEnd(new Http2HeadersAdaptor(headers));
    }
  }

  private void metricsEnd(Stream stream) {
    if (metrics != null) {
      metrics.responseEnd(stream.metric, stream.bytesRead());
    }
  }

  @Override
  public synchronized void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
    StreamImpl stream = (StreamImpl) stream(streamId);
    if (stream != null) {
      Handler<HttpClientPush> pushHandler = stream.pushHandler;
      if (pushHandler != null) {
        Http2Stream promisedStream = handler.connection().stream(promisedStreamId);
        StreamImpl pushStream = new StreamImpl(this, context, true);
        pushStream.init(promisedStream);
        HttpClientPush push = new HttpClientPush(headers, pushStream);
        if (metrics != null) {
          Object metric = metrics.requestBegin(headers.path().toString(), push);
          pushStream.metric = metric;
          metrics.requestEnd(metric, 0L);
        }
        stream.context.dispatch(push, pushHandler);
        return;
      }
    }

    Http2ClientConnection.this.handler.writeReset(promisedStreamId, Http2Error.CANCEL.code());
  }

  //
  static abstract class Stream extends VertxHttp2Stream<Http2ClientConnection> {

    private final boolean push;
    private HttpResponseHead response;
    protected Object metric;
    protected Object trace;
    private boolean requestEnded;
    private boolean responseEnded;
    protected Handler<HttpResponseHead> headHandler;
    protected Handler<Buffer> chunkHandler;
    protected Handler<MultiMap> endHandler;
    protected Handler<StreamPriority> priorityHandler;
    protected Handler<Void> drainHandler;
    protected Handler<Void> continueHandler;
    protected Handler<HttpFrame> unknownFrameHandler;
    protected Handler<Throwable> exceptionHandler;
    protected Handler<HttpClientPush> pushHandler;

    Stream(Http2ClientConnection conn, ContextInternal context, boolean push) {
      super(conn, context);

      this.push = push;
    }

    void onContinue() {
      context.emit(null, v -> handleContinue());
    }

    abstract void handleContinue();

    public Object metric() {
      return metric;
    }

    @Override
    void doWriteData(ByteBuf chunk, boolean end, Handler<AsyncResult<Void>> handler) {
      super.doWriteData(chunk, end, handler);
      if (end) {
        endRequest();
      }
    }

    @Override
    void doWriteHeaders(Http2Headers headers, boolean end, Handler<AsyncResult<Void>> handler) {
      isConnect = "CONNECT".contentEquals(headers.method());
      super.doWriteHeaders(headers, end, handler);
      if (end) {
        endRequest();
      }
    }

    protected void endRequest() {
      requestEnded = true;
      if (conn.metrics != null) {
        conn.metrics.requestEnd(metric, bytesWritten());
      }
    }

    @Override
    void onEnd(MultiMap trailers) {
      conn.metricsEnd(this);
      responseEnded = true;
      super.onEnd(trailers);
    }

    @Override
    void onReset(long code) {
      if (conn.metrics != null) {
        conn.metrics.requestReset(metric);
      }
      super.onReset(code);
    }

    @Override
    void onHeaders(Http2Headers headers, StreamPriority streamPriority) {
      if (streamPriority != null) {
        priority(streamPriority);
      }
      if (response == null) {
        int status;
        String statusMessage;
        try {
          status = Integer.parseInt(headers.status().toString());
          statusMessage = HttpResponseStatus.valueOf(status).reasonPhrase();
        } catch (Exception e) {
          handleException(e);
          writeReset(0x01 /* PROTOCOL_ERROR */);
          return;
        }
        if (status == 100) {
          onContinue();
          return;
        }
        response = new HttpResponseHead(
          HttpVersion.HTTP_2,
          status,
          statusMessage,
          new Http2HeadersAdaptor(headers));
        headers.remove(":status");

        if (conn.metrics != null) {
          conn.metrics.responseBegin(metric, response);
        }

        if (headHandler != null) {
          context.emit(response, headHandler);
        }
      }
    }

    @Override
    void onClose() {
      if (conn.metrics != null) {
        if (!requestEnded || !responseEnded) {
          conn.metrics.requestReset(metric);
        }
      }
      VertxTracer tracer = context.tracer();
      if (tracer != null && trace != null) {
        VertxException err;
        if (responseEnded && requestEnded) {
          err = null;
        } else {
          err = ConnectionBase.CLOSED_EXCEPTION;
        }
        tracer.receiveResponse(context, response, trace, err, HttpUtils.CLIENT_RESPONSE_TAG_EXTRACTOR);
      }
      if (!responseEnded) {
        onError(CLOSED_EXCEPTION);
      }
      super.onClose();
      // commented to be used later when we properly define the HTTP/2 connection expiration from the pool
      // boolean disposable = conn.streams.isEmpty();
      if (!push) {
        conn.recycle();
      } /* else {
        conn.listener.onRecycle(0, disposable);
      } */
    }
  }

  static class StreamImpl extends Stream implements HttpClientStream {

    StreamImpl(Http2ClientConnection conn, ContextInternal context, boolean push) {
      super(conn, context, push);
    }

    @Override
    public void continueHandler(Handler<Void> handler) {
      continueHandler = handler;
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
    public void drainHandler(Handler<Void> handler) {
      drainHandler = handler;
    }

    @Override
    public void exceptionHandler(Handler<Throwable> handler) {
      exceptionHandler = handler;
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
    public HttpVersion version() {
      return HttpVersion.HTTP_2;
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
      if (writable && drainHandler != null) {
        drainHandler.handle(null);
      }
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

    void handleException(Throwable exception) {
      if (exceptionHandler != null) {
        exceptionHandler.handle(exception);
      }
    }

    @Override
    public void writeHead(HttpRequestHead request, boolean chunked, ByteBuf buf, boolean end, StreamPriority priority, boolean connect, Handler<AsyncResult<Void>> handler) {
      priority(priority);
      conn.context.emit(null, v -> {
        writeHeaders(request, buf, end, priority, connect, handler);
      });
    }

    private void writeHeaders(HttpRequestHead request, ByteBuf buf, boolean end, StreamPriority priority, boolean connect, Handler<AsyncResult<Void>> handler) {
      Http2Headers headers = new DefaultHttp2Headers();
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
        e= end;
      }
      if (request.headers != null && request.headers.size() > 0) {
        for (Map.Entry<String, String> header : request.headers) {
          headers.add(HttpUtils.toLowerCase(header.getKey()), header.getValue());
        }
      }
      if (conn.client.getOptions().isTryUseCompression() && headers.get(HttpHeaderNames.ACCEPT_ENCODING) == null) {
        headers.set(HttpHeaderNames.ACCEPT_ENCODING, DEFLATE_GZIP);
      }
      try {
        createStream(request, headers, handler);
      } catch (Http2Exception ex) {
        if (handler != null) {
          handler.handle(context.failedFuture(ex));
        }
        handleException(ex);
        return;
      }
      if (buf != null) {
        doWriteHeaders(headers, false, null);
        doWriteData(buf, e, handler);
      } else {
        doWriteHeaders(headers, e, handler);
      }
    }

    private void createStream(HttpRequestHead head, Http2Headers headers, Handler<AsyncResult<Void>> handler) throws Http2Exception {
      int id = this.conn.handler.encoder().connection().local().lastStreamCreated();
      if (id == 0) {
        id = 1;
      } else {
        id += 2;
      }
      head.id = id;
      head.remoteAddress = conn.remoteAddress();
      Http2Stream stream = this.conn.handler.encoder().connection().local().createStream(id, false);
      init(stream);
      if (conn.metrics != null) {
        metric = conn.metrics.requestBegin(headers.path().toString(), head);
      }
      VertxTracer tracer = context.tracer();
      if (tracer != null) {
        BiConsumer<String, String> headers_ = headers::add;
        trace = tracer.sendRequest(context, SpanKind.RPC, conn.client.getOptions().getTracingPolicy(), head, headers.method().toString(), headers_, HttpUtils.CLIENT_HTTP_REQUEST_TAG_EXTRACTOR);
      }
    }

    @Override
    public void writeBuffer(ByteBuf buf, boolean end, Handler<AsyncResult<Void>> listener) {
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
      long code = cause instanceof StreamResetException ? ((StreamResetException)cause).getCode() : 0;
      conn.context.emit(code, this::writeReset);
    }

    @Override
    public HttpClientConnection connection() {
      return conn;
    }
  }

  @Override
  protected void handleIdle() {
    if (handler.connection().local().numActiveStreams() > 0) {
      super.handleIdle();
    }
  }

  public static VertxHttp2ConnectionHandler<Http2ClientConnection> createHttp2ConnectionHandler(
    HttpClientImpl client,
    ClientMetrics metrics,
    EventLoopContext context,
    Object socketMetric,
    Handler<Http2ClientConnection> c) {
    HttpClientOptions options = client.getOptions();
    VertxHttp2ConnectionHandler<Http2ClientConnection> handler = new VertxHttp2ConnectionHandlerBuilder<Http2ClientConnection>()
      .server(false)
      .useCompression(client.getOptions().isTryUseCompression())
      .gracefulShutdownTimeoutMillis(0) // So client close tests don't hang 30 seconds - make this configurable later but requires HTTP/1 impl
      .initialSettings(client.getOptions().getInitialSettings())
      .connectionFactory(connHandler -> new Http2ClientConnection(client, context, connHandler, metrics))
      .logEnabled(options.getLogActivity())
      .build();
    HttpClientMetrics met = client.metrics();
    handler.addHandler(conn -> {
      if (options.getHttp2ConnectionWindowSize() > 0) {
        conn.setWindowSize(options.getHttp2ConnectionWindowSize());
      }
      if (metrics != null) {
        Object m = socketMetric;
        if (m == null)  {
          m = met.connected(conn.remoteAddress(), conn.remoteName());
          met.endpointConnected(metrics);
        }
        conn.metric(m);
      }
      c.handle(conn);
    });
    handler.removeHandler(conn -> {
      if (metrics != null) {
        met.endpointDisconnected(metrics);
      }
      conn.tryEvict();
    });
    return handler;
  }
}
