/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.util.CharsetUtil;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.Metrics;
import io.vertx.core.queue.Queue;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;
import java.net.URISyntaxException;

import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED;
import static io.netty.handler.codec.http.HttpHeaderValues.MULTIPART_FORM_DATA;
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
public class HttpServerRequestImpl implements HttpServerRequest {

  private static final Logger log = LoggerFactory.getLogger(HttpServerRequestImpl.class);

  private final Http1xServerConnection conn;

  private DefaultHttpRequest request;
  private io.vertx.core.http.HttpVersion version;
  private io.vertx.core.http.HttpMethod method;
  private String rawMethod;
  private String uri;
  private String path;
  private String query;

  private HttpServerResponseImpl response;
  private HttpServerRequestImpl next;
  private Object metric;

  private Handler<Buffer> dataHandler;
  private Handler<Throwable> exceptionHandler;

  //Cache this for performance
  private MultiMap params;
  private MultiMap headers;
  private String absoluteURI;

  private Handler<HttpServerFileUpload> uploadHandler;
  private Handler<Void> endHandler;
  private MultiMap attributes;
  private HttpPostRequestDecoder decoder;
  private boolean ended;
  private long bytesRead;

  private boolean paused;
  private Queue<Buffer> pending;

  HttpServerRequestImpl(Http1xServerConnection conn, DefaultHttpRequest request) {
    this.conn = conn;
    this.request = request;
  }

  DefaultHttpRequest getRequest() {
    synchronized (conn) {
      return request;
    }
  }

  void setRequest(DefaultHttpRequest request) {
    synchronized (conn) {
      this.request = request;
    }
  }

  private Queue<Buffer> pendingQueue() {
    if (pending == null) {
      pending = Queue.queue(conn.getContext(), 8);
      pending.writableHandler(v -> conn.doResume());
      pending.emptyHandler(v -> {
        if (ended) {
          doEnd();
        }
      });
      pending.handler(this::handleData);
    }
    return pending;
  }

  private void enqueueData(Buffer chunk) {
    // We queue requests if paused or a request is in progress to prevent responses being written in the wrong order
    if (!pendingQueue().add(chunk)) {
      // We only pause when we are actively called by the connection
      conn.doPause();
    }
  }

  void handleContent(Buffer buffer) {
    if (paused || pending != null) {
      enqueueData(buffer);
    } else {
      handleData(buffer);
    }
  }

  void handleBegin() {
    if (Metrics.METRICS_ENABLED) {
      reportRequestBegin();
    }
    response = new HttpServerResponseImpl((VertxInternal) conn.vertx(), conn, request, metric);
    if (conn.handle100ContinueAutomatically) {
      check100();
    }
    conn.requestHandler.handle(this);
  }

  void appendRequest(HttpServerRequestImpl next) {
    HttpServerRequestImpl current = this;
    while (current.next != null) {
      current = current.next;
    }
    current.next = next;
  }

  HttpServerRequestImpl nextRequest() {
    return next;
  }

  void handlePipelined() {
    paused = false;
    boolean end = ended;
    ended = false;
    handleBegin();
    if (!paused && pending != null && pending.size() > 0) {
      pending.resume();
    }
    if (end) {
      handleEnd();
    }
  }

  private void reportRequestBegin() {
    if (conn.metrics != null) {
      metric = conn.metrics.requestBegin(conn.metric(), this);
    }
  }

  private void check100() {
    if (HttpUtil.is100ContinueExpected(request)) {
      conn.write100Continue();
    }
  }

  Object metric() {
    return metric;
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
      String sMethod = request.method().toString();
      try {
        method = io.vertx.core.http.HttpMethod.valueOf(sMethod);
      } catch (IllegalArgumentException e) {
        method = io.vertx.core.http.HttpMethod.OTHER;
      }
    }
    return method;
  }

  @Override
  public String rawMethod() {
    if (rawMethod == null) {
      rawMethod = request.method().toString();
    }
    return rawMethod;
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
  public HttpServerResponseImpl response() {
    return response;
  }

  @Override
  public MultiMap headers() {
    if (headers == null) {
      headers = new HeadersAdaptor(request.headers());
    }
    return headers;
  }

  @Override
  public String getHeader(String headerName) {
    return headers().get(headerName);
  }

  @Override
  public String getHeader(CharSequence headerName) {
    return headers().get(headerName);
  }

  @Override
  public MultiMap params() {
    if (params == null) {
      params = HttpUtils.params(uri());
    }
    return params;
  }

  @Override
  public String getParam(String paramName) {
    return params().get(paramName);
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
      if (!isEnded()) {
        pendingQueue().pause();
      }
      return this;
    }
  }

  @Override
  public HttpServerRequest fetch(long amount) {
    synchronized (conn) {
      pendingQueue().take(amount);
      return this;
    }
  }

  @Override
  public HttpServerRequest resume() {
    synchronized (conn) {
      if (!isEnded()) {
        pendingQueue().resume();
      }
      return this;
    }
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
  public boolean isSSL() {
    return conn.isSSL();
  }

  @Override
  public SocketAddress remoteAddress() {
    return conn.remoteAddress();
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
  public SSLSession sslSession() {
    return conn.sslSession();
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
    ws.connectNow();
    return ws;
  }

  @Override
  public HttpServerRequest setExpectMultipart(boolean expect) {
    synchronized (conn) {
      checkEnded();
      if (expect) {
        if (decoder == null) {
          String contentType = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
          if (contentType != null) {
            HttpMethod method = request.method();
            if (isValidMultipartContentType(contentType) && isValidMultipartMethod(method)) {
              decoder = new HttpPostRequestDecoder(new NettyFileUploadDataFactory(conn.vertx(), this, () -> uploadHandler), request);
            }
          }
        }
      } else {
        decoder = null;
      }
      return this;
    }
  }

  private boolean isValidMultipartContentType(String contentType) {
    return MULTIPART_FORM_DATA.regionMatches(true, 0, contentType, 0, MULTIPART_FORM_DATA.length())
      || APPLICATION_X_WWW_FORM_URLENCODED.regionMatches(true, 0, contentType, 0, APPLICATION_X_WWW_FORM_URLENCODED.length());
  }

  private boolean isValidMultipartMethod(HttpMethod method) {
    return method.equals(HttpMethod.POST) || method.equals(HttpMethod.PUT) || method.equals(HttpMethod.PATCH)
      || method.equals(HttpMethod.DELETE);
  }

  @Override
  public boolean isExpectMultipart() {
    synchronized (conn) {
      return decoder != null;
    }
  }

  @Override
  public SocketAddress localAddress() {
    return conn.localAddress();
  }

  @Override
  public boolean isEnded() {
    synchronized (conn) {
      return response != null && ended && (pending == null || pending.isEmpty());
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

  private void handleData(Buffer data) {
    synchronized (conn) {
      bytesRead += data.length();
      if (decoder != null) {
        try {
          decoder.offer(new DefaultHttpContent(data.getByteBuf()));
        } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
          handleException(e);
        }
      }
      if (dataHandler != null) {
        dataHandler.handle(data);
      }
    }
  }

  void handleEnd() {
    synchronized (conn) {
      ended = true;
      if (isEnded()) {
        doEnd();
      }
    }
  }

  private void doEnd() {
    if (decoder != null) {
      endDecode();
    }
    // If there have been uploads then we let the last one call the end handler once any fileuploads are complete
    if (endHandler != null) {
      endHandler.handle(null);
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
    synchronized (conn) {
      if (!isEnded()) {
        Handler<Throwable> handler = this.exceptionHandler;
        if (handler != null) {
          conn.getContext().runOnContext(v -> handler.handle(t));
        }
      }
      if (!response.ended()) {
        if (METRICS_ENABLED) {
          reportRequestReset();
        }
        response.handleException(t);
      }
    }
  }

  private void reportRequestReset() {
    if (conn.metrics != null) {
      conn.metrics.requestReset(metric);
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
      attributes = new CaseInsensitiveHeaders();
    }
    return attributes;
  }


  private static String urlDecode(String str) {
    return QueryStringDecoder.decodeComponent(str, CharsetUtil.UTF_8);
  }

}
