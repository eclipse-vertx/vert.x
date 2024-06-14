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
import io.netty.handler.codec.http2.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.http.impl.headers.Http2HeadersAdaptor;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.impl.MessageWrite;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.streams.WriteStream;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http2ClientConnection extends Http2ConnectionBase implements HttpClientConnectionInternal {

  private final HttpClientBase client;
  private final ClientMetrics metrics;
  private final HostAndPort authority;
  private final boolean pooled;
  private Handler<Void> evictionHandler = DEFAULT_EVICTION_HANDLER;
  private Handler<Long> concurrencyChangeHandler = DEFAULT_CONCURRENCY_CHANGE_HANDLER;
  private long expirationTimestamp;
  private boolean evicted;

  Http2ClientConnection(HttpClientBase client,
                        ContextInternal context,
                        HostAndPort authority,
                        VertxHttp2ConnectionHandler connHandler,
                        ClientMetrics metrics, boolean pooled) {
    super(context, connHandler);
    this.metrics = metrics;
    this.client = client;
    this.authority = authority;
    this.pooled = pooled;
  }

  @Override
  public HostAndPort authority() {
    return authority;
  }

  @Override
  public boolean pooled() {
    return pooled;
  }

  @Override
  public Http2ClientConnection evictionHandler(Handler<Void> handler) {
    evictionHandler = handler;
    return this;
  }

  @Override
  public Http2ClientConnection concurrencyChangeHandler(Handler<Long> handler) {
    concurrencyChangeHandler = handler;
    return this;
  }

  public long concurrency() {
    long concurrency = remoteSettings().getMaxConcurrentStreams();
    long http2MaxConcurrency = client.options().getHttp2MultiplexingLimit() <= 0 ? Long.MAX_VALUE : client.options().getHttp2MultiplexingLimit();
    if (http2MaxConcurrency > 0) {
      concurrency = Math.min(concurrency, http2MaxConcurrency);
    }
    return concurrency;
  }

  @Override
  public long activeStreams() {
    return handler.connection().numActiveStreams();
  }

  @Override
  boolean onGoAwaySent(GoAway goAway) {
    boolean goneAway = super.onGoAwaySent(goAway);
    if (goneAway) {
      // Eagerly evict from the pool
      tryEvict();
    }
    return goneAway;
  }

  @Override
  boolean onGoAwayReceived(GoAway goAway) {
    boolean goneAway = super.onGoAwayReceived(goAway);
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
      evictionHandler.handle(null);
    }
  }

  @Override
  protected void concurrencyChanged(long concurrency) {
    int limit = client.options().getHttp2MultiplexingLimit();
    if (limit > 0) {
      concurrency = Math.min(concurrency, limit);
    }
    concurrencyChangeHandler.handle(concurrency);
  }

  @Override
  public HttpClientMetrics metrics() {
    return client.metrics();
  }

  HttpClientStream upgradeStream(Object metric, Object trace, ContextInternal context) throws Exception {
    StreamImpl stream = createStream2(context);
    stream.init(handler.connection().stream(1));
    stream.metric = metric;
    stream.trace = trace;
    stream.requestEnded = true;
    return stream;
  }

  @Override
  public Future<HttpClientStream> createStream(ContextInternal context) {
    synchronized (this) {
      try {
        StreamImpl stream = createStream2(context);
        return context.succeededFuture(stream);
      } catch (Exception e) {
        return context.failedFuture(e);
      }
    }
  }

  private StreamImpl createStream2(ContextInternal context) {
    return new StreamImpl(this, context, false);
  }

  private void recycle() {
    int timeout = client.options().getHttp2KeepAliveTimeout();
    expirationTimestamp = timeout > 0 ? System.currentTimeMillis() + timeout * 1000L : 0L;
  }

  @Override
  public boolean isValid() {
    return expirationTimestamp == 0 || System.currentTimeMillis() <= expirationTimestamp;
  }

  @Override
  public long lastResponseReceivedTimestamp() {
    return 0L;
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
    protected boolean requestEnded;
    private boolean responseEnded;
    protected Handler<HttpResponseHead> headHandler;
    protected Handler<Buffer> chunkHandler;
    protected Handler<MultiMap> endHandler;
    protected Handler<StreamPriority> priorityHandler;
    protected Handler<Void> drainHandler;
    protected Handler<Void> continueHandler;
    protected Handler<MultiMap> earlyHintsHandler;
    protected Handler<HttpFrame> unknownFrameHandler;
    protected Handler<Throwable> exceptionHandler;
    protected Handler<HttpClientPush> pushHandler;
    protected Handler<Void> closeHandler;

    Stream(Http2ClientConnection conn, ContextInternal context, boolean push) {
      super(conn, context);

      this.push = push;
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
    void doWriteHeaders(Http2Headers headers, boolean end, boolean checkFlush, Promise<Void> promise) {
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
        } else if (status == 103) {
          MultiMap headersMultiMap = HeadersMultiMap.httpHeaders();
          removeStatusHeaders(headers);
          for (Map.Entry<CharSequence, CharSequence> header : headers) {
            headersMultiMap.add(header.getKey(), header.getValue());
          }
          onEarlyHints(headersMultiMap);
          return;
        }
        response = new HttpResponseHead(
          HttpVersion.HTTP_2,
          status,
          statusMessage,
          new Http2HeadersAdaptor(headers));
        removeStatusHeaders(headers);

        if (conn.metrics != null) {
          conn.metrics.responseBegin(metric, response);
        }

        if (headHandler != null) {
          context.emit(response, headHandler);
        }
      }
    }

    private void removeStatusHeaders(Http2Headers headers) {
      headers.remove(HttpHeaders.PSEUDO_STATUS);
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
        conn.recycle();
      } /* else {
        conn.listener.onRecycle(0, disposable);
      } */
      if (closeHandler != null) {
        closeHandler.handle(null);
      }
    }
  }

  static class StreamImpl extends Stream implements HttpClientStream {

    StreamImpl(Http2ClientConnection conn, ContextInternal context, boolean push) {
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
    public StreamImpl drainHandler(Handler<Void> handler) {
      drainHandler = handler;
      return this;
    }

    @Override
    public StreamImpl exceptionHandler(Handler<Throwable> handler) {
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
    public boolean isNotWritable() {
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
    void handleWriteQueueDrained() {
      Handler<Void> handler = drainHandler;
      if (handler != null) {
        context.dispatch(null, handler);
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
    public Future<Void> writeHead(HttpRequestHead request, boolean chunked, ByteBuf buf, boolean end, StreamPriority priority, boolean connect) {
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

    private void writeHeaders(HttpRequestHead request, ByteBuf buf, boolean end, StreamPriority priority, boolean connect, Promise<Void> promise) {
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
      if (conn.client.options().isDecompressionSupported() && headers.get(HttpHeaderNames.ACCEPT_ENCODING) == null) {
        headers.set(HttpHeaderNames.ACCEPT_ENCODING, Http1xClientConnection.determineCompressionAcceptEncoding());
      }
      try {
        createStream(request, headers);
      } catch (Http2Exception ex) {
        promise.fail(ex);
        handleException(ex);
        return;
      }
      if (buf != null) {
        doWriteHeaders(headers, false, false, null);
        doWriteData(buf, e, promise);
      } else {
        doWriteHeaders(headers, e, true, promise);
      }
    }

    private void createStream(HttpRequestHead head, Http2Headers headers) throws Http2Exception {
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
        BiConsumer<String, String> headers_ = (key, val) -> new Http2HeadersAdaptor(headers).add(key, val);
        String operation = head.traceOperation;
        if (operation == null) {
          operation = headers.method().toString();
        }
        trace = tracer.sendRequest(context, SpanKind.RPC, conn.client.options().getTracingPolicy(), head, operation, headers_, HttpUtils.CLIENT_HTTP_REQUEST_TAG_EXTRACTOR);
      }
    }

    @Override
    public Future<Void> writeBuffer(ByteBuf buf, boolean end) {
      Promise<Void> promise = context.promise();
      writeData(buf, end, promise);
      return promise.future();
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
      long code;
      if (cause instanceof StreamResetException) {
        code = ((StreamResetException)cause).getCode();
      } else if (cause instanceof java.util.concurrent.TimeoutException) {
        code = 0x08L; // CANCEL
      } else {
        code = 0L;
      }
      conn.context.emit(code, this::writeReset);
    }

    @Override
    public HttpClientConnectionInternal connection() {
      return conn;
    }
  }

  @Override
  protected void handleIdle(IdleStateEvent event) {
    if (handler.connection().local().numActiveStreams() > 0) {
      super.handleIdle(event);
    }
  }

  public static VertxHttp2ConnectionHandler<Http2ClientConnection> createHttp2ConnectionHandler(
    HttpClientBase client,
    ClientMetrics metrics,
    ContextInternal context,
    boolean upgrade,
    Object socketMetric,
    HostAndPort authority,
    boolean pooled) {
    HttpClientOptions options = client.options();
    HttpClientMetrics met = client.metrics();
    VertxHttp2ConnectionHandler<Http2ClientConnection> handler = new VertxHttp2ConnectionHandlerBuilder<Http2ClientConnection>()
      .server(false)
      .useDecompression(client.options().isDecompressionSupported())
      .gracefulShutdownTimeoutMillis(0) // So client close tests don't hang 30 seconds - make this configurable later but requires HTTP/1 impl
      .initialSettings(client.options().getInitialSettings())
      .connectionFactory(connHandler -> {
        Http2ClientConnection conn = new Http2ClientConnection(client, context, authority, connHandler, metrics, pooled);
        if (metrics != null) {
          Object m = socketMetric;
          conn.metric(m);
        }
        return conn;
      })
      .logEnabled(options.getLogActivity())
      .build();
    handler.addHandler(conn -> {
      if (options.getHttp2ConnectionWindowSize() > 0) {
        conn.setWindowSize(options.getHttp2ConnectionWindowSize());
      }
      if (metrics != null) {
        if (!upgrade)  {
          met.endpointConnected(metrics);
        }
      }
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
