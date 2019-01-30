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

import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerFileUpload;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.tracing.VertxTracer;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;
import java.net.URISyntaxException;
import java.nio.channels.ClosedChannelException;
import java.util.*;

import static io.vertx.core.spi.metrics.Metrics.METRICS_ENABLED;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ServerRequestImpl extends VertxHttp2Stream<Http2ServerConnection> implements HttpServerRequest {

  private static final Logger log = LoggerFactory.getLogger(HttpServerRequestImpl.class);

  private final String serverOrigin;
  private final Http2ServerResponseImpl response;
  private final Http2Headers headers;
  private MultiMap headersMap;
  private MultiMap params;
  private HttpMethod method;
  private String rawMethod;
  private String absoluteURI;
  private String uri;
  private String path;
  private String query;
  private MultiMap attributes;
  private Object trace;

  private Handler<Buffer> dataHandler;
  private Handler<Void> endHandler;
  private boolean streamEnded;
  private boolean ended;
  private long bytesRead;

  private Handler<HttpServerFileUpload> uploadHandler;
  private HttpPostRequestDecoder postRequestDecoder;

  private Handler<Throwable> exceptionHandler;
  private Handler<HttpFrame> customFrameHandler;

  private Handler<StreamPriority> streamPriorityHandler;
  
  public Http2ServerRequestImpl(Http2ServerConnection conn, ContextInternal context, Http2Stream stream, HttpServerMetrics metrics,
                                String serverOrigin, Http2Headers headers, String contentEncoding, boolean writable, boolean streamEnded) {
    super(conn, context, stream, writable);

    this.serverOrigin = serverOrigin;
    this.headers = headers;
    this.streamEnded = streamEnded;

    String host = host();
    if (host == null) {
      int idx = serverOrigin.indexOf("://");
      host = serverOrigin.substring(idx + 3);
    }
    this.response = new Http2ServerResponseImpl(conn, this, method(), false, contentEncoding, host);
    if (METRICS_ENABLED && metrics != null) {
      response.metric(metrics.requestBegin(conn.metric(), this));
    }
  }

  void dispatch(Handler<HttpServerRequest> handler) {
    VertxTracer tracer = context.tracer();
    if (tracer != null) {
      List<Map.Entry<String, String>> tags = new ArrayList<>();
      tags.add(new AbstractMap.SimpleEntry<>("http.url", absoluteURI()));
      tags.add(new AbstractMap.SimpleEntry<>("http.method", method.name()));
      trace = tracer.receiveRequest(context, this, method().name(), headers(), tags);
    }
    context.dispatch(this, handler);
  }

  @Override
  void handleInterestedOpsChanged() {
    response.writabilityChanged();
  }

  @Override
  void handleException(Throwable cause) {
    Handler<Throwable> handler;
    synchronized (conn) {
      handler = ended ? null : exceptionHandler;
    }
    if (handler != null) {
      context.dispatch(cause, handler);
    }
    response.handleException(cause);
  }

  @Override
  void handleClose() {
    Handler<Throwable> handler;
    Throwable failure;
    synchronized (conn) {
      handler = streamEnded ? null : exceptionHandler;
      if (!streamEnded && (!ended || !response.ended())) {
        failure = ConnectionBase.CLOSED_EXCEPTION;
      } else {
        failure = null;
      }
    }
    VertxTracer tracer = context.tracer();
    if (tracer != null) {
      List<Map.Entry<String, String>> tags = Collections.singletonList(new AbstractMap.SimpleEntry<>("http.status_code", "" + response.getStatusCode()));
      tracer.sendResponse(context, failure == null ? response : null, trace, failure, tags);
    }
    if (handler != null) {
      context.dispatch(new ClosedChannelException(), handler);
    }
    response.handleClose();
  }

  @Override
  void handleCustomFrame(int type, int flags, Buffer buff) {
    if (customFrameHandler != null) {
      context.dispatch(new HttpFrameImpl(type, flags, buff), customFrameHandler);
    }
  }

  void handleData(Buffer data) {
    bytesRead += data.length();
    if (postRequestDecoder != null) {
      try {
        postRequestDecoder.offer(new DefaultHttpContent(data.getByteBuf()));
      } catch (Exception e) {
        handleException(e);
      }
    }
    if (dataHandler != null) {
      context.dispatch(data, dataHandler);
    }
  }

  void handleEnd(MultiMap trailers) {
    streamEnded = true;
    ended = true;
    conn.reportBytesRead(bytesRead);
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
    if (endHandler != null) {
      context.dispatch(endHandler);
    }
  }

  @Override
  void handleReset(long errorCode) {
    Handler<Throwable> handler;
    synchronized (conn) {
      handler = ended ? null : exceptionHandler;
      ended = true;
    }
    if (handler != null) {
      context.dispatch(new StreamResetException(errorCode), exceptionHandler);
    }
    response.handleReset(errorCode);
  }

  private void checkEnded() {
    if (ended) {
      throw new IllegalStateException("Request has already been read");
    }
  }

  @Override
  public HttpServerRequest exceptionHandler(Handler<Throwable> handler) {
    synchronized (conn) {
      exceptionHandler = handler;
    }
    return this;
  }

  @Override
  public HttpServerRequest handler(Handler<Buffer> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkEnded();
      }
      dataHandler = handler;
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
      endHandler = handler;
    }
    return this;
  }

  @Override
  public HttpVersion version() {
    return HttpVersion.HTTP_2;
  }

  @Override
  public HttpMethod method() {
    synchronized (conn) {
      if (method == null) {
        String sMethod = headers.method().toString();
        method = HttpUtils.toVertxMethod(sMethod);
      }
      return method;
    }
  }

  @Override
  public String rawMethod() {
    synchronized (conn) {
      if (rawMethod == null) {
        rawMethod = headers.method().toString();
      }
      return rawMethod;
    }
  }

  @Override
  public boolean isSSL() {
    return conn.isSsl();
  }

  @Override
  public String uri() {
    synchronized (conn) {
      if (uri == null) {
        CharSequence path = headers.path();
        if (path != null) {
          uri = path.toString();
        }
      }
      return uri;
    }
  }

  @Override
  public String path() {
    synchronized (conn) {
      if (path == null) {
        CharSequence path = headers.path();
        if (path != null) {
          this.path = HttpUtils.parsePath(path.toString());
        }
      }
      return path;
    }
  }

  @Override
  public String query() {
    synchronized (conn) {
      if (query == null) {
        CharSequence path = headers.path();
        if (path != null) {
          this.query = HttpUtils.parseQuery(path.toString());
        }
      }
      return query;
    }
  }

  @Override
  public String scheme() {
    CharSequence scheme = headers.scheme();
    return scheme != null ? scheme.toString() : null;
  }

  @Override
  public String host() {
    CharSequence authority = headers.authority();
    return authority != null ? authority.toString() : null;
  }

  @Override
  public long bytesRead() {
    synchronized (conn) {
      return bytesRead;
    }
  }


  @Override
  public Http2ServerResponseImpl response() {
    return response;
  }

  @Override
  public MultiMap headers() {
    synchronized (conn) {
      if (headersMap == null) {
        headersMap = new Http2HeadersAdaptor(headers);
      }
      return headersMap;
    }
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
    synchronized (conn) {
      if (params == null) {
        params = HttpUtils.params(uri());
      }
      return params;
    }
  }

  @Override
  public String getParam(String paramName) {
    return params().get(paramName);
  }

  @Override
  public SocketAddress remoteAddress() {
    return conn.remoteAddress();
  }

  @Override
  public SocketAddress localAddress() {
    return conn.localAddress();
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
  public String absoluteURI() {
    if (method() == HttpMethod.CONNECT) {
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
  public NetSocket netSocket() {
    return response.netSocket();
  }

  @Override
  public HttpServerRequest setExpectMultipart(boolean expect) {
    synchronized (conn) {
      checkEnded();
      if (expect) {
        if (postRequestDecoder == null) {
          CharSequence contentType = headers.get(HttpHeaderNames.CONTENT_TYPE);
          if (contentType != null) {
            io.netty.handler.codec.http.HttpMethod method = io.netty.handler.codec.http.HttpMethod.valueOf(headers.method().toString());
            String lowerCaseContentType = contentType.toString().toLowerCase();
            boolean isURLEncoded = lowerCaseContentType.startsWith(HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.toString());
            if ((lowerCaseContentType.startsWith(HttpHeaderValues.MULTIPART_FORM_DATA.toString()) || isURLEncoded) &&
                (method == io.netty.handler.codec.http.HttpMethod.POST ||
                    method == io.netty.handler.codec.http.HttpMethod.PUT ||
                    method == io.netty.handler.codec.http.HttpMethod.PATCH ||
                    method == io.netty.handler.codec.http.HttpMethod.DELETE)) {
              HttpRequest req = new DefaultHttpRequest(
                  io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                  method,
                  headers.path().toString());
              req.headers().add(HttpHeaderNames.CONTENT_TYPE, contentType);
              postRequestDecoder = new HttpPostRequestDecoder(new NettyFileUploadDataFactory(context, this, () -> uploadHandler), req);
            }
          }
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
        attributes = new CaseInsensitiveHeaders();
      }
      return attributes;
    }
  }

  @Override
  public String getFormAttribute(String attributeName) {
    return formAttributes().get(attributeName);
  }

  @Override
  public ServerWebSocket upgrade() {
    throw new UnsupportedOperationException("HTTP/2 request cannot be upgraded to a websocket");
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
  public HttpServerRequest streamPriorityHandler(Handler<StreamPriority> handler) {
    synchronized (conn) {
      streamPriorityHandler = handler;
    }
    return this;
  }

  @Override
  void handlePriorityChange(StreamPriority streamPriority) {
    Handler<StreamPriority> handler;
    boolean priorityChanged = false;
    synchronized (conn) {
      handler = streamPriorityHandler;
      if (streamPriority != null && !streamPriority.equals(streamPriority())) {
        priority(streamPriority);
        priorityChanged = true;
      }
    }
    if (handler != null && priorityChanged) {
      context.dispatch(streamPriority, handler);
    }
  }

  @Override
  public StreamPriority streamPriority() {
    return priority();
  }
  
  

}
