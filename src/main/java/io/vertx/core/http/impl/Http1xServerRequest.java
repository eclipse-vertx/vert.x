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

import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.headers.HeadersAdaptor;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.streams.impl.InboundBuffer;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import java.net.URISyntaxException;
import java.util.Map;

import static io.vertx.core.spi.metrics.Metrics.METRICS_ENABLED;

/**
 * This class is optimised for performance when used on the same event loop that is was passed to the handler with.
 * However it can be used safely from other threads.
 *
 * The internal state is protected by using the connection as a lock. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * It's important we don't have different locks for connection and request/response to avoid deadlock conditions
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Http1xServerRequest implements HttpServerRequestInternal, io.vertx.core.spi.observability.HttpRequest {

  private static final Logger log = LoggerFactory.getLogger(Http1xServerRequest.class);

  private final Http1xServerConnection conn;
  final ContextInternal context;

  private HttpRequest request;
  private io.vertx.core.http.HttpVersion version;
  private io.vertx.core.http.HttpMethod method;
  private String uri;
  private String path;
  private String query;

  // Accessed on event loop
  Http1xServerRequest next;
  Object metric;
  Object trace;

  private Http1xServerResponse response;

  // Cache this for performance
  private MultiMap params;
  private MultiMap headers;
  private String absoluteURI;

  private HttpEventHandler eventHandler;
  private Handler<HttpServerFileUpload> uploadHandler;
  private MultiMap attributes;
  private HttpPostRequestDecoder decoder;
  private boolean ended;
  private long bytesRead;
  private InboundBuffer<Object> pending;

  Http1xServerRequest(Http1xServerConnection conn, HttpRequest request, ContextInternal context) {
    this.conn = conn;
    this.context = context;
    this.request = request;
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

  void setRequest(HttpRequest request) {
    synchronized (conn) {
      this.request = request;
    }
  }

  private InboundBuffer<Object> pendingQueue() {
    if (pending == null) {
      pending = new InboundBuffer<>(conn.getContext(), 8);
      pending.drainHandler(v -> conn.doResume());
      pending.handler(buffer -> {
        if (buffer == InboundBuffer.END_SENTINEL) {
          onEnd();
        } else {
          onData((Buffer) buffer);
        }
      });
    }
    return pending;
  }

  void handleContent(Buffer buffer) {
    InboundBuffer<Object> queue;
    synchronized (conn) {
      queue = pending;
    }
    if (queue != null) {
      // We queue requests if paused or a request is in progress to prevent responses being written in the wrong order
      if (!queue.write(buffer)) {
        // We only pause when we are actively called by the connection
        conn.doPause();
      }
    } else {
      context.execute(buffer, this::onData);
    }
  }

  void handleBegin() {
    response = new Http1xServerResponse((VertxInternal) conn.vertx(), context, conn, request, metric);
    if (conn.handle100ContinueAutomatically) {
      check100();
    }
  }

  /**
   * Enqueue a pipelined request.
   *
   * @param request the enqueued request
   */
  void enqueue(Http1xServerRequest request) {
    Http1xServerRequest current = this;
    while (current.next != null) {
      current = current.next;
    }
    current.next = request;
  }

  /**
   * @return the next request following this one
   */
  Http1xServerRequest next() {
    return next;
  }

  private void check100() {
    if (HttpUtil.is100ContinueExpected(request)) {
      conn.write100Continue();
    }
  }

  public Object metric() {
    return metric;
  }

  Object trace() {
    return trace;
  }

  @Override
  public Context context() {
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
  public @Nullable String host() {
    return getHeader(HttpHeaderNames.HOST);
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
    if (headers == null) {
      HttpHeaders reqHeaders = request.headers();
      if (reqHeaders instanceof MultiMap) {
        headers = (MultiMap) reqHeaders;
      } else {
        headers = new HeadersAdaptor(reqHeaders);
      }
    }
    return headers;
  }

  @Override
  public MultiMap params() {
    if (params == null) {
      params = HttpUtils.params(uri());
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
    synchronized (conn) {
      pendingQueue().pause();
      return this;
    }
  }

  @Override
  public HttpServerRequest fetch(long amount) {
    synchronized (conn) {
      pendingQueue().fetch(amount);
      return this;
    }
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
      try {
        absoluteURI = HttpUtils.absoluteURI(conn.getServerOrigin(), this);
      } catch (URISyntaxException e) {
        log.error("Failed to create abs uri", e);
      }
    }
    return absoluteURI;
  }

  @Override
  public SocketAddress remoteAddress() {
    return conn.remoteAddress();
  }

  @Override
  public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
    return conn.peerCertificateChain();
  }

  @Override
  public Future<NetSocket> toNetSocket() {
    if (method() != HttpMethod.CONNECT) {
      return context.failedFuture("HTTP method must be CONNECT to upgrade the connection to a net socket");
    }
    return response.netSocket();
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
    return webSocket().map(ws -> {
      ws.accept();
      return ws;
    });
  }

  /**
   * @return a future of the un-accepted WebSocket
   */
  Future<ServerWebSocket> webSocket() {
    PromiseInternal<ServerWebSocket> promise = context.promise();
    webSocket(promise);
    return promise.future();
  }

  /**
   * Handle the request when a WebSocket upgrade header is present.
   */
  private void webSocket(PromiseInternal<ServerWebSocket> promise) {
    Buffer body = Buffer.buffer();
    boolean[] failed = new boolean[1];
    handler(buff -> {
      if (!failed[0]) {
        body.appendBuffer(buff);
        if (body.length() > 8192) {
          failed[0] = true;
          // Request Entity Too Large
          response.setStatusCode(413).end();
          response.close();
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
          factory.setMaxLimit(conn.options.getMaxFormAttributeSize());
          decoder = new HttpPostRequestDecoder(factory, request);
        }
      } else {
        decoder = null;
      }
      return this;
    }
  }

  @Override
  public boolean isExpectMultipart() {
    synchronized (conn) {
      return decoder != null;
    }
  }

  @Override
  public boolean isEnded() {
    synchronized (conn) {
      return ended && (pending == null || (!pending.isPaused() && pending.isEmpty()));
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
          decoder.offer(new DefaultHttpContent(data.getByteBuf()));
        } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
          handleException(e);
        }
      }
      handler = eventHandler;
    }
    if (handler != null) {
      eventHandler.handleChunk(data);
    }
  }

  void handleEnd() {
    InboundBuffer<Object> queue;
    synchronized (conn) {
      ended = true;
      queue = pending;
    }
    if (queue != null) {
      queue.write(InboundBuffer.END_SENTINEL);
    } else {
      onEnd();
    }
  }

  private void onEnd() {
    HttpEventHandler handler;
    synchronized (conn) {
      if (decoder != null) {
        endDecode();
      }
      handler = eventHandler;
    }
    // If there have been uploads then we let the last one call the end handler once any fileuploads are complete
    if (handler != null) {
      handler.handleEnd();
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
    } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
      handleException(e);
    } catch (HttpPostRequestDecoder.EndOfDataDecoderException e) {
      // ignore this as it is expected
    } finally {
      decoder.destroy();
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
      ((NettyFileUpload)upload).handleException(t);
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
  public HttpServerRequest streamPriorityHandler(Handler<StreamPriority> handler) {
    return this;
  }

  @Override
  public Map<String, Cookie> cookieMap() {
    return (Map)response.cookies();
  }

  @Override
  public HttpServerRequest routed(String route) {
    if (METRICS_ENABLED && !response.ended() && conn.metrics != null) {
      conn.metrics.requestRouted(metric, route);
    }
    return this;
  }
}
