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

package io.vertx.core.http.impl.http1;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.*;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.*;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.headers.HeadersAdaptor;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.http.impl.SafeContentDecoder;
import io.vertx.core.internal.http.ContentDecoder;
import io.vertx.core.internal.http.FlowController;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.internal.concurrent.InboundMessageQueue;
import io.vertx.core.net.impl.HostAndPortImpl;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.Set;

import static io.vertx.core.spi.metrics.Metrics.METRICS_ENABLED;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Http1ServerRequest extends HttpServerRequestBase implements io.vertx.core.spi.observability.HttpRequest, FlowController {

  private static final HostAndPort NULL_HOST_AND_PORT = HostAndPort.create("", -1);

  // Immutable
  private final Http1ServerConnection conn;
  final ContextInternal context;
  private final HttpRequest request;

  // Event loop
  boolean reportMetricsFailed;
  Http1ServerRequest next;

  // Written on the event loop before the request handler is called, read on both threads (happens-before by emit)
  private Http1ServerResponse response;
  Object metric;
  Object trace;

  // Event loop and context thread using double checked locking
  private volatile InboundMessageQueue<Buffer> queue;

  // Context thread
  private io.vertx.core.http.HttpVersion version;
  private io.vertx.core.http.HttpMethod method;
  private HostAndPort authority;
  private String uri;
  private String path;
  private String query;
  private MultiMap headers;
  private String absoluteURI;
  private HttpEventHandler eventHandler;
  private Throwable failure;
  private long bytesRead;
  private Handler<HttpServerFileUpload> uploadHandler;
  private MultiMap attributes;
  private boolean expectMultipart;
  private FormDecoder formDecoder;
  private ContentDecoder<?> contentDecoder;
  private boolean contentStarted;
  private boolean decoderClosed;
  private boolean trailersReceived;

  Http1ServerRequest(Http1ServerConnection conn, HttpRequest request, ContextInternal context) {
    super(conn.queryParamDecoder());
    this.conn = conn;
    this.context = context;
    this.request = request;
  }

  private InboundMessageQueue<Buffer> queue() {
    return queue(true);
  }

  private InboundMessageQueue<Buffer> queue(boolean create) {
    InboundMessageQueue<Buffer> ref = queue;
    if (create && ref == null) {
      synchronized (this) {
        ref = queue;
        if (ref == null) {
          ref = new InboundMessageQueue<>(context.eventLoop(), context.executor()) {
            @Override
            protected void handleMessage(Buffer elt) {
              if (elt == HttpUtils.END_OF_STREAM) {
                onEnd();
              } else {
                onData(elt);
              }
            }
            @Override
            protected void handleResume() {
              conn.resumeRequest(Http1ServerRequest.this);
            }
            @Override
            protected void handlePause() {
              conn.doPause();
            }
          };
          queue = ref;
        }
      }
    }
    return ref;
  }

  private HttpEventHandler eventHandler(boolean create) {
    if (eventHandler == null && create) {
      eventHandler = new HttpEventHandler(context, failure);
    }
    return eventHandler;
  }

  public HttpRequest nettyRequest() {
    return request;
  }

  void handleBegin(boolean keepAlive) {
    if (METRICS_ENABLED) {
      reportRequestBegin();
    }
    response = new Http1ServerResponse(context.owner(), context, conn, request, metric, keepAlive);
    if (conn.handle100ContinueAutomatically) {
      check100();
    }
  }

  void handleContent(Buffer buffer) {
    InboundMessageQueue<Buffer> queue = queue();
    boolean drain = queue.add(buffer);
    if (drain) {
      queue.drain();
    }
  }

  void handleEnd() {
    InboundMessageQueue<Buffer> queue = queue(false);
    if (queue != null) {
      context.execute(this, Http1ServerRequest::onTrailersReceived);
      handleEnd(queue);
    } else {
      context.execute(this, Http1ServerRequest::onEnd);
    }
  }

  private void onTrailersReceived() {
    trailersReceived = true;
  }

  private void handleEnd(InboundMessageQueue<Buffer> queue) {
    boolean drain = queue.add(HttpUtils.END_OF_STREAM);
    if (drain) {
      queue.drain();
    }
  }

  private void check100() {
    if (HttpUtil.is100ContinueExpected(request)) {
      response.writeContinue();
    }
  }

  public Object metric() {
    return metric;
  }

  Object trace() {
    return trace;
  }

  @Override
  public ContextInternal context() {
    return context;
  }

  @Override
  public ByteBufAllocator allocator() {
    return conn.channel().alloc();
  }

  @Override
  public long id() {
    return 0L;
  }

  @Override
  public io.vertx.core.http.HttpVersion version() {
    if (version == null) {
      io.netty.handler.codec.http.HttpVersion nettyVersion = request.protocolVersion();
      if (nettyVersion == io.netty.handler.codec.http.HttpVersion.HTTP_1_0) {
        version = HttpVersion.HTTP_1_0;
      } else if (nettyVersion == io.netty.handler.codec.http.HttpVersion.HTTP_1_1) {
        version = HttpVersion.HTTP_1_1;
      }
    }
    return version;
  }

  @Override
  public io.vertx.core.http.HttpMethod method() {
    if (method == null) {
      method = io.vertx.core.http.HttpMethod.fromNetty(request.method());
    }
    return method;
  }

  @Override
  public String uri() {
    if (uri == null) {
      uri = request.uri();
    }
    return uri;
  }

  @Override
  public String path() {
    if (path == null) {
      path = HttpUtils.parsePath(uri());
    }
    return path;
  }

  @Override
  public String query() {
    if (query == null) {
      query = HttpUtils.parseQuery(uri());
    }
    return query;
  }

  @Override
  public boolean isValidAuthority() {
    HostAndPort authority = this.authority;
    if (authority == NULL_HOST_AND_PORT) {
      return false;
    }
    if (authority != null) {
      return true;
    }
    String host = getHeader(HttpHeaderNames.HOST);
    if (host == null || !HostAndPortImpl.isValidAuthority(host)) {
      this.authority = NULL_HOST_AND_PORT;
      return false;
    }
    return true;
  }

  @Override
  public HostAndPort authority() {
    HostAndPort authority = this.authority;
    if (authority == NULL_HOST_AND_PORT) {
      return null;
    }
    if (authority == null) {
      String host = getHeader(HttpHeaderNames.HOST);
      if (host == null) {
        this.authority = NULL_HOST_AND_PORT;
        return null;
      }
      // it's fine to have a benign race here as long as HostAndPort is immutable
      // to ensure safe publication
      authority = HostAndPort.parseAuthority(host, -1);
      this.authority = authority;
    }
    return authority;
  }

  @Override
  public HostAndPort authority(boolean real) {
    return real ? null : authority();
  }

  @Override
  public long bytesRead() {
    return bytesRead;
  }

  @Override
  public Http1ServerResponse response() {
    return response;
  }

  @Override
  public MultiMap headers() {
    MultiMap headers = this.headers;
    if (headers == null) {
      HttpHeaders reqHeaders = request.headers();
      if (reqHeaders instanceof MultiMap) {
        headers = (MultiMap) reqHeaders;
      } else {
        headers = new HeadersAdaptor(reqHeaders);
      }
      this.headers = headers;
    }
    return headers;
  }

  @Override
  public HttpServerRequest handler(Handler<Buffer> handler) {
    if (handler != null) {
      checkEnded();
    }
    HttpEventHandler eventHandler = eventHandler(handler != null);
    if (eventHandler != null) {
      eventHandler.chunkHandler(handler);
    }
    return this;
  }

  @Override
  public HttpServerRequest exceptionHandler(Handler<Throwable> handler) {
    HttpEventHandler eventHandler = eventHandler(handler != null);
    if (eventHandler != null) {
      eventHandler.exceptionHandler(handler);
    }
    return this;
  }

  @Override
  public Http1ServerRequest pause() {
    queue().pause();
    return this;
  }

  @Override
  public Http1ServerRequest fetch(long amount) {
    queue().fetch(amount);
    return this;
  }

  @Override
  public Http1ServerRequest resume() {
    return fetch(Long.MAX_VALUE);
  }

  @Override
  public HttpServerRequest endHandler(Handler<Void> handler) {
    if (handler != null) {
      checkEnded();
    }
    HttpEventHandler eventHandler = eventHandler(handler != null);
    if (eventHandler != null) {
      eventHandler.endHandler(handler);
    }
    return this;
  }

  @Override
  public String scheme() {
    return isSSL() ? "https" : "http";
  }

  @Override
  public String absoluteURI() {
    if (absoluteURI == null) {
      absoluteURI = HttpUtils.absoluteURI(conn.serverOrigin(), this);
    }
    return absoluteURI;
  }

  @Override
  public SocketAddress remoteAddress() {
    return super.remoteAddress();
  }

  @Override
  public Future<NetSocket> toNetSocket() {
    return response.netSocket(method(), headers());
  }

  @Override
  public HttpServerRequest uploadHandler(Handler<HttpServerFileUpload> handler) {
    if (handler != null) {
      checkEnded();
    }
    uploadHandler = handler;
    return this;
  }

  @Override
  public MultiMap formAttributes() {
    return attributes();
  }

  @Override
  public String getFormAttribute(String attributeName) {
    return formAttributes().get(attributeName);
  }

  @Override
  public Future<ServerWebSocket> toWebSocket() {
    return webSocketHandshake().compose(handshake -> handshake.accept());
  }

  /**
   * @return a future of the un-accepted WebSocket
   */
  Future<ServerWebSocketHandshake> webSocketHandshake() {
    PromiseInternal<ServerWebSocketHandshake> promise = context.promise();
    webSocketHandshake(promise);
    return promise.future();
  }

  /**
   * Handle the request when a WebSocket upgrade header is present.
   */
  private void webSocketHandshake(PromiseInternal<ServerWebSocketHandshake> promise) {
    BufferInternal body = BufferInternal.buffer();
    boolean[] failed = new boolean[1];
    handler(buff -> {
      if (!failed[0]) {
        body.appendBuffer(buff);
        if (body.length() > 8192) {
          failed[0] = true;
          // Request Entity Too Large
          response.setStatusCode(413).end();
          conn.close();
        }
      }
    });
    exceptionHandler(promise::tryFail);
    endHandler(v -> {
      if (!failed[0]) {
        // Handle the request once we have the full body.
        FullHttpRequest fullRequest = new DefaultFullHttpRequest(
          request.protocolVersion(),
          request.method(),
          request.uri(),
          body.getByteBuf(),
          request.headers(),
          EmptyHttpHeaders.INSTANCE
        );
        conn.createWebSocket(this, fullRequest, promise);
      }
    });
    // In case we were paused
    resume();
  }

  @Override
  public HttpServerRequest setExpectMultipart(boolean expect) {
    checkEnded();
    if (expect) {
      if (formDecoder == null) {
        FormDecoder decoder = new FormDecoder(context, request, conn.maxFormAttributeSize(), conn.maxFormFields(),
          conn.maxFormBufferedBytes(), () -> uploadHandler, attributes(), this::onException, chunk -> {}, v -> {});
        contentDecoder(decoder);
        formDecoder = decoder;
      }
    } else if (formDecoder != null) {
      contentDecoder(null);
      formDecoder = null;
    }
    expectMultipart = expect;
    return this;
  }

  @Override
  public boolean isExpectMultipart() {
    return expectMultipart;
  }

  @Override
  public boolean isEnded() {
    return response.requestEnded;
  }

  @Override
  public HttpServerRequest customFrameHandler(Handler<HttpFrame> handler) {
    return this;
  }

  @Override
  public HttpServerConnection connection() {
    return conn;
  }

  @Override
  public Future<Buffer> body() {
    checkEnded();
    return eventHandler(true).body();
  }

  @Override
  public Future<Void> end() {
    checkEnded();
    return eventHandler(true).end();
  }

  private void onData(Buffer data) {
    bytesRead += data.length();
    context.dispatch(((BufferInternal)data).getByteBuf(), currentDecoder());
    HttpEventHandler handler = eventHandler;
    if (handler != null) {
      handler.handleChunk(data);
    }
  }

  private void onEnd() {
    trailersReceived = true;
    if (METRICS_ENABLED) {
      reportRequestComplete();
    }
    ContentDecoder<?> decoder = currentDecoder();
    ContextInternal prev = context.beginDispatch();
    try {
      decoder.handleEnd();
      if (!decoderClosed) {
        decoderClosed = true;
        decoder.handleClose(null);
      }
    } finally {
      context.endDispatch(prev);
    }
    response.requestEnded = true;
    // If there have been uploads then we let the last one call the end handler once any fileuploads are complete
    HttpEventHandler handler = eventHandler;
    if (handler != null) {
      handler.handleEnd();
    }
    if (response.trailersSent) {
      response.tryClose();
    }
  }

  private void reportRequestComplete() {
    HttpServerMetrics metrics = conn.httpMetrics;
    if (metrics != null) {
      metrics.requestEnd(metric, this, bytesRead);
    }
  }

  @Override
  public <T> Future<T> contentDecoder(ContentDecoder<T> decoder) {
    if (contentStarted) {
      throw new IllegalStateException("Content has already started");
    }
    ContentDecoder<?> previous = contentDecoder;
    Future<T> result;
    if (decoder != null) {
      SafeContentDecoder<T> safeDecoder = new SafeContentDecoder<>(decoder, context);
      safeDecoder.init(this);
      contentDecoder = safeDecoder;
      result = safeDecoder.future();
    } else {
      contentDecoder = null;
      result = context.succeededFuture();
    }
    if (previous != null) {
      previous.handleClose(HttpUtils.STREAM_CLOSED_EXCEPTION);
    }
    return result;
  }

  private ContentDecoder<?> currentDecoder() {
    if (!contentStarted) {
      contentStarted = true;
      if (contentDecoder == null) {
        contentDecoder = ContentDecoder.NULL;
        contentDecoder.init(this);
      }
    }
    return contentDecoder;
  }

  private void reportRequestBegin() {
    HttpServerMetrics metrics = conn.httpMetrics;
    if (metrics != null) {
      metric = metrics.requestBegin(remoteAddress(), this);
    }
    VertxTracer tracer = context.tracer();
    if (tracer != null) {
      trace = tracer.receiveRequest(context, SpanKind.RPC, conn.tracingPolicy(), this, request.method().name(), request.headers(), HttpUtils.SERVER_REQUEST_TAG_EXTRACTOR);
    }
  }

  void handleException(Throwable t) {
    context.execute(t, this::onException);
  }

  void handleClosed() {
    context.execute(this, Http1ServerRequest::onClosed);
  }

  void onException(Throwable t) {
    HttpEventHandler handler = null;
    Http1ServerResponse resp = null;
    FormDecoder decoder = null;
    if (!isEnded()) {
      handler = eventHandler;
      if (handler == null && failure == null) {
        failure = t;
      }
      decoder = formDecoder;
    }
    if (!response.ended()) {
      if (METRICS_ENABLED) {
        reportRequestReset(t);
      }
      resp = response;
    }
    if (resp != null) {
      resp.onException(t);
    }
    if (decoder != null) {
      decoder.failUpload(t);
    }
    if (handler != null) {
      handler.handleException(t);
    }
  }

  void onClosed() {
    onException(HttpUtils.CONNECTION_CLOSED_EXCEPTION);
    response.tryClose();
    if (!decoderClosed) {
      decoderClosed = true;
      ContentDecoder<?> decoder = currentDecoder();
      ContextInternal prev = context.beginDispatch();
      try {
        Throwable cause = trailersReceived ? null : HttpUtils.CONNECTION_CLOSED_EXCEPTION;
        if (cause != null) {
          contentDecoder = ContentDecoder.NULL;
        }
        decoder.handleClose(cause);
      } finally {
        context.endDispatch(prev);
      }
    }
  }

  private void reportRequestReset(Throwable err) {
    if (conn.httpMetrics != null) {
      conn.httpMetrics.requestReset(metric);
    }
    VertxTracer tracer = context.tracer();
    if (tracer != null) {
      tracer.sendResponse(context, null, trace, err, TagExtractor.empty());
    }
  }

  private void checkEnded() {
    if (isEnded()) {
      throw new IllegalStateException("Request has already been read");
    }
  }

  private MultiMap attributes() {
    // Create it lazily
    if (attributes == null) {
      attributes = MultiMap.caseInsensitiveMultiMap();
    }
    return attributes;
  }

  @Override
  public HttpServerRequest streamPriorityHandler(Handler<StreamPriority> handler) {
    return this;
  }

  @Override
  public DecoderResult decoderResult() {
    return request.decoderResult();
  }

  @Override
  public Set<Cookie> cookies() {
    return (Set) response.cookies();
  }

  @Override
  public Set<Cookie> cookies(String name) {
    return (Set) response.cookies().getAll(name);
  }

  @Override
  public Cookie getCookie(String name) {
    return response.cookies()
      .get(name);
  }

  @Override
  public Cookie getCookie(String name, String domain, String path) {
    return response.cookies()
      .get(name, domain, path);
  }

  @Override
  public HttpServerRequest routed(String route) {
    if (METRICS_ENABLED && !response.ended() && conn.httpMetrics != null) {
      conn.httpMetrics.requestRouted(metric, route);
    }
    return this;
  }
}
