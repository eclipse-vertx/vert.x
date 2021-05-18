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

import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http2.Http2Headers;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerFileUpload;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.impl.headers.Http2HeadersAdaptor;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import java.net.URISyntaxException;
import java.nio.channels.ClosedChannelException;
import java.util.Map;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ServerRequest extends Http2ServerStream implements HttpServerRequestInternal, io.vertx.core.spi.observability.HttpRequest {

  private static final Logger log = LoggerFactory.getLogger(Http1xServerRequest.class);

  private final String serverOrigin;
  private final MultiMap headersMap;
  private final String scheme;
  private final TracingPolicy tracingPolicy;

  // Accessed on event loop
  private Object trace;

  // Accessed on context thread
  private MultiMap params;
  private String absoluteURI;
  private MultiMap attributes;
  private HttpEventHandler eventHandler;
  private boolean streamEnded;
  private boolean ended;
  private Handler<HttpServerFileUpload> uploadHandler;
  private HttpPostRequestDecoder postRequestDecoder;
  private Handler<HttpFrame> customFrameHandler;
  private Handler<StreamPriority> streamPriorityHandler;

  Http2ServerRequest(Http2ServerConnection conn,
                     TracingPolicy tracingPolicy,
                     ContextInternal context,
                     String serverOrigin,
                     Http2Headers headers,
                     String contentEncoding,
                     boolean streamEnded) {
    super(conn, context, headers, contentEncoding, serverOrigin);

    String scheme = headers.get(":scheme") != null ? headers.get(":scheme").toString() : null;
    headers.remove(":method");
    headers.remove(":scheme");
    headers.remove(":path");
    headers.remove(":authority");

    this.serverOrigin = serverOrigin;
    this.streamEnded = streamEnded;
    this.scheme = scheme;
    this.headersMap = new Http2HeadersAdaptor(headers);
    this.tracingPolicy = tracingPolicy;
  }

  private HttpEventHandler eventHandler(boolean create) {
    if (eventHandler == null && create) {
      eventHandler = new HttpEventHandler(context);
    }
    return eventHandler;
  }

  void dispatch(Handler<HttpServerRequest> handler) {
    VertxTracer tracer = context.tracer();
    if (tracer != null) {
      trace = tracer.receiveRequest(context, SpanKind.RPC, tracingPolicy, this, method().name(), headers(), HttpUtils.SERVER_REQUEST_TAG_EXTRACTOR);
    }
    context.emit(this, handler);
  }

  @Override
  void handleWritabilityChanged(boolean writable) {
    response.handlerWritabilityChanged(writable);
  }

  @Override
  void handleException(Throwable cause) {
    boolean notify;
    synchronized (conn) {
      notify = !ended;
    }
    if (notify) {
      notifyException(cause);
    }
    response.handleException(cause);
  }

  private void notifyException(Throwable failure) {
    InterfaceHttpData upload = null;
    HttpEventHandler handler;
    synchronized (conn) {
      if (postRequestDecoder != null) {
        upload = postRequestDecoder.currentPartialHttpData();
      }
      handler = eventHandler;
    }
    if (handler != null) {
      handler.handleException(failure);
    }
    if (upload instanceof NettyFileUpload) {
      ((NettyFileUpload)upload).handleException(failure);
    }
  }

  @Override
  void onClose() {
    VertxTracer tracer = context.tracer();
    Object trace = this.trace;
    if (tracer != null && trace != null) {
      Throwable failure;
      synchronized (conn) {
        if (!streamEnded && (!ended || !response.ended())) {
          failure = ConnectionBase.CLOSED_EXCEPTION;
        } else {
          failure = null;
        }
      }
      tracer.sendResponse(context, failure == null ? response : null, trace, failure, HttpUtils.SERVER_RESPONSE_TAG_EXTRACTOR);
    }
    super.onClose();
  }

  @Override
  void handleClose() {
    super.handleClose();
    boolean notify;
    synchronized (conn) {
      notify = !streamEnded;
    }
    if (notify) {
      notifyException(new ClosedChannelException());
    }
    response.handleClose();
  }

  @Override
  void handleCustomFrame(HttpFrame frame) {
    if (customFrameHandler != null) {
      customFrameHandler.handle(frame);
    }
  }

  void handleData(Buffer data) {
    if (postRequestDecoder != null) {
      try {
        postRequestDecoder.offer(new DefaultHttpContent(data.getByteBuf()));
      } catch (Exception e) {
        handleException(e);
      }
    }
    HttpEventHandler handler = eventHandler;
    if (handler != null) {
      handler.handleChunk(data);
    }
  }

  void handleEnd(MultiMap trailers) {
    HttpEventHandler handler;
    synchronized (conn) {
      streamEnded = true;
      ended = true;
      if (postRequestDecoder != null) {
        try {
          postRequestDecoder.offer(LastHttpContent.EMPTY_LAST_CONTENT);
          while (postRequestDecoder.hasNext()) {
            InterfaceHttpData data = postRequestDecoder.next();
            if (data instanceof Attribute) {
              Attribute attr = (Attribute) data;
              try {
                formAttributes().add(attr.getName(), attr.getValue());
              } catch (Exception e) {
                // Will never happen, anyway handle it somehow just in case
                handleException(e);
              } finally {
                attr.release();
              }
            }
          }
        } catch (HttpPostRequestDecoder.EndOfDataDecoderException e) {
          // ignore this as it is expected
        } catch (Exception e) {
          handleException(e);
        } finally {
          postRequestDecoder.destroy();
        }
      }
      handler = eventHandler;
    }
    if (handler != null) {
      handler.handleEnd();
    }
  }

  @Override
  void handleReset(long errorCode) {
    boolean notify;
    synchronized (conn) {
      notify = !ended;
      ended = true;
    }
    if (notify) {
      notifyException(new StreamResetException(errorCode));
    }
    response.handleReset(errorCode);
  }

  private void checkEnded() {
    if (ended) {
      throw new IllegalStateException("Request has already been read");
    }
  }

  @Override
  public Context context() {
    return context;
  }

  @Override
  public HttpServerRequest exceptionHandler(Handler<Throwable> handler) {
    synchronized (conn) {
      HttpEventHandler eventHandler = eventHandler(handler != null);
      if (eventHandler != null) {
        eventHandler.exceptionHandler(handler);
      }
    }
    return this;
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
    }
    return this;
  }

  @Override
  public HttpServerRequest pause() {
    synchronized (conn) {
      checkEnded();
      doPause();
    }
    return this;
  }

  @Override
  public HttpServerRequest resume() {
    return fetch(Long.MAX_VALUE);
  }

  @Override
  public HttpServerRequest fetch(long amount) {
    synchronized (conn) {
      checkEnded();
      doFetch(amount);
    }
    return this;
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
    }
    return this;
  }

  @Override
  public HttpVersion version() {
    return HttpVersion.HTTP_2;
  }

  @Override
  public String uri() {
    return uri;
  }

  @Override
  public String path() {
    synchronized (conn) {
      return uri != null ? HttpUtils.parsePath(uri) : null;
    }
  }

  @Override
  public String query() {
    synchronized (conn) {
      return uri != null ? HttpUtils.parseQuery(uri) : null;
    }
  }

  @Override
  public String scheme() {
    return scheme;
  }

  @Override
  public String host() {
    return host;
  }

  @Override
  public long bytesRead() {
    return super.bytesRead();
  }

  @Override
  public Http2ServerResponse response() {
    return response;
  }

  @Override
  public MultiMap headers() {
    return headersMap;
  }

  @Override
  public MultiMap params() {
    synchronized (conn) {
      if (params == null) {
        params = HttpUtils.params(uri());
      }
      return params;
    }
  }

  @Override
  public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
    return conn.peerCertificateChain();
  }

  @Override
  public SocketAddress remoteAddress() {
    return conn.remoteAddress();
  }

  @Override
  public String absoluteURI() {
    if (method == HttpMethod.CONNECT) {
      return null;
    }
    synchronized (conn) {
      if (absoluteURI == null) {
        try {
          absoluteURI = HttpUtils.absoluteURI(serverOrigin, this);
        } catch (URISyntaxException e) {
          log.error("Failed to create abs uri", e);
        }
      }
      return absoluteURI;
    }
  }

  @Override
  public Future<NetSocket> toNetSocket() {
    return response.netSocket();
  }

  @Override
  public HttpServerRequest setExpectMultipart(boolean expect) {
    synchronized (conn) {
      checkEnded();
      if (expect) {
        if (postRequestDecoder == null) {
          String contentType = headersMap.get(HttpHeaderNames.CONTENT_TYPE);
          if (contentType == null) {
            throw new IllegalStateException("Request must have a content-type header to decode a multipart request");
          }
          if (!HttpUtils.isValidMultipartContentType(contentType)) {
            throw new IllegalStateException("Request must have a valid content-type header to decode a multipart request");
          }
          if (!HttpUtils.isValidMultipartMethod(method.toNetty())) {
            throw new IllegalStateException("Request method must be one of POST, PUT, PATCH or DELETE to decode a multipart request");
          }
          HttpRequest req = new DefaultHttpRequest(
            io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
            method.toNetty(),
            uri);
          req.headers().add(HttpHeaderNames.CONTENT_TYPE, contentType);
          NettyFileUploadDataFactory factory = new NettyFileUploadDataFactory(context, this, () -> uploadHandler);
          factory.setMaxLimit(conn.options.getMaxFormAttributeSize());
          postRequestDecoder = new HttpPostRequestDecoder(factory, req);
        }
      } else {
        postRequestDecoder = null;
      }
    }
    return this;
  }

  @Override
  public boolean isExpectMultipart() {
    synchronized (conn) {
      return postRequestDecoder != null;
    }
  }

  @Override
  public HttpServerRequest uploadHandler(@Nullable Handler<HttpServerFileUpload> handler) {
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
    synchronized (conn) {
      // Create it lazily
      if (attributes == null) {
        attributes = MultiMap.caseInsensitiveMultiMap();
      }
      return attributes;
    }
  }

  @Override
  public String getFormAttribute(String attributeName) {
    return formAttributes().get(attributeName);
  }

  @Override
  public int streamId() {
    return stream.id();
  }

  @Override
  public Future<ServerWebSocket> toWebSocket() {
    return context.failedFuture("HTTP/2 request cannot be upgraded to a WebSocket");
  }

  @Override
  public boolean isEnded() {
    synchronized (conn) {
      return ended;
    }
  }

  @Override
  public HttpServerRequest customFrameHandler(Handler<HttpFrame> handler) {
    synchronized (conn) {
      customFrameHandler = handler;
    }
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

  public StreamPriority streamPriority() {
    return priority();
  }

  @Override
  public HttpServerRequest streamPriorityHandler(Handler<StreamPriority> handler) {
    synchronized (conn) {
      streamPriorityHandler = handler;
    }
    return this;
  }

  @Override
  void handlePriorityChange(StreamPriority streamPriority) {
    Handler<StreamPriority> handler;
    synchronized (conn) {
      handler = streamPriorityHandler;
    }
    if (handler != null) {
      handler.handle(streamPriority);
    }
  }

  @Override
  public Map<String, Cookie> cookieMap() {
    return (Map) response.cookies();
  }

  @Override
  public HttpServerRequest routed(String route) {
    super.routed(route);
    return this;
  }
}
