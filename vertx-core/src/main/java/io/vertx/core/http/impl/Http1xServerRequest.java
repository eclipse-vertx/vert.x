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

import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.headers.HeadersAdaptor;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.http.HttpServerRequestInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.internal.concurrent.InboundMessageChannel;
import io.vertx.core.net.impl.HostAndPortImpl;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.streams.impl.InboundBuffer;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;

import static io.vertx.core.spi.metrics.Metrics.METRICS_ENABLED;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Http1xServerRequest extends HttpServerRequestInternal implements io.vertx.core.spi.observability.HttpRequest {

  private static final HostAndPort NULL_HOST_AND_PORT = HostAndPort.create("", -1);

  private final Http1xServerConnection conn;
  final ContextInternal context;

  private HttpRequest request;
  private io.vertx.core.http.HttpVersion version;
  private io.vertx.core.http.HttpMethod method;
  private HostAndPort authority;
  private String uri;
  private String path;
  private String query;

  // Accessed on event loop
  Object metric;
  Object trace;
  boolean reportMetricsFailed;

  private Http1xServerResponse response;

  // Cache this for performance
  private Charset paramsCharset = StandardCharsets.UTF_8;
  private MultiMap params;
  private boolean semicolonIsNormalCharInParams;
  private MultiMap headers;
  private String absoluteURI;

  private HttpEventHandler eventHandler;
  private Handler<HttpServerFileUpload> uploadHandler;
  private MultiMap attributes;
  private boolean expectMultipart;
  private HttpPostRequestDecoder decoder;
  private boolean ended;
  private long bytesRead;
  private final InboundMessageChannel<Object> queue;

  Http1xServerRequest(Http1xServerConnection conn, HttpRequest request, ContextInternal context) {
    this.conn = conn;
    this.context = context;
    this.request = request;
    this.queue = new InboundMessageChannel<>(context.eventLoop(), context.executor()) {
      @Override
      protected void handleMessage(Object elt) {
        if (elt == InboundBuffer.END_SENTINEL) {
          onEnd();
        } else {
          onData((Buffer) elt);
        }
      }
      @Override
      protected void handleResume() {
        conn.doResume();
      }
      @Override
      protected void handlePause() {
        conn.doPause();
      }
    };
  }

  private HttpEventHandler eventHandler(boolean create) {
    if (eventHandler == null && create) {
      eventHandler = new HttpEventHandler(context);
    }
    return eventHandler;
  }

  HttpRequest nettyRequest() {
    synchronized (conn) {
      return request;
    }
  }

  void handleBegin(boolean keepAlive) {
    if (METRICS_ENABLED) {
      reportRequestBegin();
    }
    response = new Http1xServerResponse(context.owner(), context, conn, request, metric, keepAlive);
    if (conn.handle100ContinueAutomatically) {
      check100();
    }
  }

  void handleContent(Buffer buffer) {
    boolean drain = queue.add(buffer);
    if (drain) {
      queue.drain();
    }
  }

  void handleEnd() {
    boolean drain = queue.add(InboundBuffer.END_SENTINEL);
    if (drain) {
      queue.drain();
    }
  }

  private void check100() {
    if (HttpUtil.is100ContinueExpected(request)) {
      conn.write100Continue(null);
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
  public int id() {
    return 0;
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
  public long bytesRead() {
    synchronized (conn) {
      return bytesRead;
    }
  }

  @Override
  public Http1xServerResponse response() {
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
  public HttpServerRequest setParamsCharset(String charset) {
    Objects.requireNonNull(charset, "Charset must not be null");
    Charset current = paramsCharset;
    paramsCharset = Charset.forName(charset);
    if (!paramsCharset.equals(current)) {
      params = null;
    }
    return this;
  }

  @Override
  public String getParamsCharset() {
    return paramsCharset.name();
  }

  @Override
  public MultiMap params(boolean semicolonIsNormalChar) {
    if (params == null || semicolonIsNormalChar != semicolonIsNormalCharInParams) {
      params = HttpUtils.params(uri(), paramsCharset, semicolonIsNormalChar);
      semicolonIsNormalCharInParams = semicolonIsNormalChar;
    }
    return params;
  }

  @Override
  public HttpServerRequest handler(Handler<Buffer> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkEnded();
      }
      HttpEventHandler eventHandler = eventHandler(handler != null);
      if (eventHandler != null) {
        eventHandler.chunkHandler(handler);
      }
      return this;
    }
  }

  @Override
  public HttpServerRequest exceptionHandler(Handler<Throwable> handler) {
    synchronized (conn) {
      HttpEventHandler eventHandler = eventHandler(handler != null);
      if (eventHandler != null) {
        eventHandler.exceptionHandler(handler);
      }
      return this;
    }
  }

  @Override
  public HttpServerRequest pause() {
    queue.pause();
    return this;
  }

  @Override
  public HttpServerRequest fetch(long amount) {
    queue.fetch(amount);
    return this;
  }

  @Override
  public HttpServerRequest resume() {
    return fetch(Long.MAX_VALUE);
  }

  @Override
  public HttpServerRequest endHandler(Handler<Void> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkEnded();
      }
      HttpEventHandler eventHandler = eventHandler(handler != null);
      if (eventHandler != null) {
        eventHandler.endHandler(handler);
      }
      return this;
    }
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
    synchronized (conn) {
      if (handler != null) {
        checkEnded();
      }
      uploadHandler = handler;
      return this;
    }
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
        request = new DefaultFullHttpRequest(
          request.protocolVersion(),
          request.method(),
          request.uri(),
          body.getByteBuf(),
          request.headers(),
          EmptyHttpHeaders.INSTANCE
        );
        conn.createWebSocket(this, promise);
      }
    });
    // In case we were paused
    resume();
  }

  @Override
  public HttpServerRequest setExpectMultipart(boolean expect) {
    synchronized (conn) {
      checkEnded();
      expectMultipart = expect;
      if (expect) {
        if (decoder == null) {
          String contentType = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
          if (contentType == null) {
            throw new IllegalStateException("Request must have a content-type header to decode a multipart request");
          }
          if (!HttpUtils.isValidMultipartContentType(contentType)) {
            throw new IllegalStateException("Request must have a valid content-type header to decode a multipart request");
          }
          if (!HttpUtils.isValidMultipartMethod(request.method())) {
            throw new IllegalStateException("Request method must be one of POST, PUT, PATCH or DELETE to decode a multipart request");
          }
          NettyFileUploadDataFactory factory = new NettyFileUploadDataFactory(context, this, () -> uploadHandler);
          HttpServerOptions options = conn.options;
          factory.setMaxLimit(options.getMaxFormAttributeSize());
          int maxFields = options.getMaxFormFields();
          int maxBufferedBytes = options.getMaxFormBufferedBytes();
          decoder = new HttpPostRequestDecoder(factory, request, HttpConstants.DEFAULT_CHARSET, maxFields, maxBufferedBytes);
        }
      } else {
        decoder = null;
      }
      return this;
    }
  }

  @Override
  public synchronized boolean isExpectMultipart() {
    return expectMultipart;
  }

  @Override
  public boolean isEnded() {
    synchronized (conn) {
      return ended;
    }
  }

  @Override
  public HttpServerRequest customFrameHandler(Handler<HttpFrame> handler) {
    return this;
  }

  @Override
  public HttpConnection connection() {
    return conn;
  }

  @Override
  public synchronized Future<Buffer> body() {
    checkEnded();
    return eventHandler(true).body();
  }

  @Override
  public synchronized Future<Void> end() {
    checkEnded();
    return eventHandler(true).end();
  }

  private void onData(Buffer data) {
    HttpEventHandler handler;
    synchronized (conn) {
      bytesRead += data.length();
      if (decoder != null) {
        try {
          decoder.offer(new DefaultHttpContent(((BufferInternal)data).getByteBuf()));
        } catch (HttpPostRequestDecoder.ErrorDataDecoderException |
                 HttpPostRequestDecoder.TooLongFormFieldException |
                 HttpPostRequestDecoder.TooManyFormFieldsException e) {
          decoder.destroy();
          decoder = null;
          handleException(e);
        }
      }
      handler = eventHandler;
    }
    if (handler != null) {
      eventHandler.handleChunk(data);
    }
  }

  private void onEnd() {
    if (METRICS_ENABLED) {
      reportRequestComplete();
    }
    HttpEventHandler handler;
    synchronized (conn) {
      if (decoder != null) {
        endDecode();
      }
      ended = true;
      handler = eventHandler;
    }
    // If there have been uploads then we let the last one call the end handler once any fileuploads are complete
    if (handler != null) {
      handler.handleEnd();
    }
  }

  private void reportRequestComplete() {
    HttpServerMetrics metrics = conn.metrics;
    if (metrics != null) {
      metrics.requestEnd(metric, this, bytesRead);
      conn.flushBytesRead();
    }
  }

  private void reportRequestBegin() {
    HttpServerMetrics metrics = conn.metrics;
    if (metrics != null) {
      metric = metrics.requestBegin(conn.metric(), this);
    }
    VertxTracer tracer = context.tracer();
    if (tracer != null) {
      trace = tracer.receiveRequest(context, SpanKind.RPC, conn.tracingPolicy(), this, request.method().name(), request.headers(), HttpUtils.SERVER_REQUEST_TAG_EXTRACTOR);
    }
  }

  private void endDecode() {
    try {
      decoder.offer(LastHttpContent.EMPTY_LAST_CONTENT);
      while (decoder.hasNext()) {
        InterfaceHttpData data = decoder.next();
        if (data instanceof Attribute) {
          Attribute attr = (Attribute) data;
          try {
            attributes().add(attr.getName(), attr.getValue());
          } catch (Exception e) {
            // Will never happen, anyway handle it somehow just in case
            handleException(e);
          } finally {
            attr.release();
          }
        }
      }
    } catch (HttpPostRequestDecoder.ErrorDataDecoderException |
             HttpPostRequestDecoder.TooLongFormFieldException |
             HttpPostRequestDecoder.TooManyFormFieldsException e) {
      handleException(e);
    }  catch (HttpPostRequestDecoder.EndOfDataDecoderException e) {
      // ignore this as it is expected
    } finally {
      decoder.destroy();
      decoder = null;
    }
  }

  void handleException(Throwable t) {
    HttpEventHandler handler = null;
    Http1xServerResponse resp = null;
    InterfaceHttpData upload = null;
    synchronized (conn) {
      if (!isEnded()) {
        handler = eventHandler;
        if (decoder != null) {
          upload = decoder.currentPartialHttpData();
        }
      }
      if (!response.ended()) {
        if (METRICS_ENABLED) {
          reportRequestReset(t);
        }
        resp = response;
      }
    }
    if (resp != null) {
      resp.handleException(t);
    }
    if (upload instanceof NettyFileUpload) {
      ((NettyFileUpload) upload).handleException(t);
    }
    if (handler != null) {
      handler.handleException(t);
    }
  }

  private void reportRequestReset(Throwable err) {
    if (conn.metrics != null) {
      conn.metrics.requestReset(metric);
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
  public HttpServerRequest streamPriorityHandler(Handler<StreamPriorityBase> handler) {
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
    if (METRICS_ENABLED && !response.ended() && conn.metrics != null) {
      conn.metrics.requestRouted(metric, route);
    }
    return this;
  }
}
