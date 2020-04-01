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
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.headers.HeadersAdaptor;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
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
public class Http1xServerRequest implements HttpServerRequest {

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

  private Handler<Buffer> dataHandler;
  private Handler<Throwable> exceptionHandler;

  // Cache this for performance
  private MultiMap params;
  private MultiMap headers;
  private String absoluteURI;

  private Handler<HttpServerFileUpload> uploadHandler;
  private Handler<Void> endHandler;
  private MultiMap attributes;
  private HttpPostRequestDecoder decoder;
  private boolean ended;
  private long bytesRead;
  private InboundBuffer<Object> pending;
  private Buffer body;
  private Promise<Buffer> bodyPromise;

  Http1xServerRequest(Http1xServerConnection conn, HttpRequest request) {
    this.conn = conn;
    this.context = conn.getContext().duplicate();
    this.request = request;
  }

  /**
   *
   * @return
   */
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
      onData(buffer);
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

  Object metric() {
    return metric;
  }

  Object trace() {
    return trace;
  }

  @Override
  public io.vertx.core.http.HttpVersion version() {
    if (version == null) {
      io.netty.handler.codec.http.HttpVersion nettyVersion = request.protocolVersion();
      if (nettyVersion == io.netty.handler.codec.http.HttpVersion.HTTP_1_0) {
        version = HttpVersion.HTTP_1_0;
      } else if (nettyVersion == io.netty.handler.codec.http.HttpVersion.HTTP_1_1) {
        version = HttpVersion.HTTP_1_1;
      } else {
        sendNotImplementedAndClose();
        throw new IllegalStateException("Unsupported HTTP version: " + nettyVersion);
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
      dataHandler = handler;
      return this;
    }
  }

  @Override
  public HttpServerRequest exceptionHandler(Handler<Throwable> handler) {
    synchronized (conn) {
      this.exceptionHandler = handler;
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
      endHandler = handler;
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
  public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
    return conn.peerCertificateChain();
  }

  @Override
  public NetSocket netSocket() {
    synchronized (conn) {
      return response.netSocket(method() == io.vertx.core.http.HttpMethod.CONNECT);
    }
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
  public ServerWebSocket upgrade() {
    ServerWebSocketImpl ws = conn.createWebSocket(this);
    if (ws == null) {
      throw new IllegalStateException("Can't upgrade this request");
    }
    ws.accept();
    return ws;
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
          decoder = new HttpPostRequestDecoder(new NettyFileUploadDataFactory(conn.getContext(), this, () -> uploadHandler), request);
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
    if (bodyPromise == null) {
      bodyPromise = Promise.promise();
      body = Buffer.buffer();
    }
    return bodyPromise.future();
  }

  private void onData(Buffer data) {
    Handler<Buffer> handler;
    synchronized (conn) {
      bytesRead += data.length();
      if (decoder != null) {
        try {
          decoder.offer(new DefaultHttpContent(data.getByteBuf()));
        } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
          handleException(e);
        }
      }
      if (body != null) {
        body.appendBuffer(data);
      }
      handler = dataHandler;
      if (handler == null) {
        return;
      }
    }
    context.emit(data, handler);
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
    Handler<Void> handler;
    Promise<Buffer> bodyPromise;
    Buffer body;
    synchronized (conn) {
      if (decoder != null) {
        endDecode();
      }
      handler = endHandler;
      bodyPromise = this.bodyPromise;
      body = this.body;
    }
    // If there have been uploads then we let the last one call the end handler once any fileuploads are complete
    if (handler != null) {
      context.emit(null, handler);
    }
    if (body != null) {
      bodyPromise.tryComplete(body);
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
    Handler<Throwable> handler = null;
    Http1xServerResponse resp = null;
    InterfaceHttpData upload = null;
    Promise<Buffer> bodyPromise;
    synchronized (conn) {
      if (!isEnded()) {
        handler = exceptionHandler;
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
      bodyPromise = this.bodyPromise;
      this.bodyPromise = null;
      this.body = null;
    }
    if (resp != null) {
      resp.handleException(t);
    }
    if (upload instanceof NettyFileUpload) {
      ((NettyFileUpload)upload).handleException(t);
    }
    if (handler != null) {
      context.emit(t, handler);
    }
    if (bodyPromise != null) {
      bodyPromise.tryFail(t);
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

  private void sendNotImplementedAndClose() {
    response().setStatusCode(501).end();
    response().close();
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
}
