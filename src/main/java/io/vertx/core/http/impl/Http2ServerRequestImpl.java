/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerFileUpload;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.HttpServerMetrics;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import java.net.URISyntaxException;
import java.nio.channels.ClosedChannelException;

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
  private String absoluteURI;
  private String uri;
  private String path;
  private String query;
  private MultiMap attributes;

  private Handler<Buffer> dataHandler;
  private Handler<Void> endHandler;
  private boolean ended;
  private long bytesRead;

  private Handler<HttpServerFileUpload> uploadHandler;
  private HttpPostRequestDecoder decoder;

  private Handler<Throwable> exceptionHandler;
  private Handler<HttpFrame> unknownFrameHandler;

  public Http2ServerRequestImpl(
      HttpServerMetrics metrics,
      Vertx vertx,
      Http2ServerConnection conn,
      String serverOrigin,
      Http2Stream stream,
      ChannelHandlerContext handlerContext,
      Http2ConnectionEncoder encoder,
      Http2Headers headers,
      String contentEncoding) {
    super(conn, stream);

    this.serverOrigin = serverOrigin;
    this.headers = headers;

    Object metric = metrics.isEnabled() ? metrics.requestBegin(conn.metric(), this) : null;
    this.response = new Http2ServerResponseImpl(metrics, metric, this, (VertxInternal) vertx, handlerContext, conn, encoder, stream, false, contentEncoding);
  }

  @Override
  void handleInterestedOpsChanged() {
    response.writabilityChanged();
  }

  @Override
  void handleException(Throwable cause) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(cause);
      response.handleError(cause);
    }
  }

  @Override
  void handleClose() {
    if (!ended) {
      ended = true;
      if (exceptionHandler != null) {
        exceptionHandler.handle(new ClosedChannelException());
      }
    }
    response.handleClose();
  }

  @Override
  void handleUnknownFrame(int type, int flags, Buffer buff) {
    if (unknownFrameHandler != null) {
      unknownFrameHandler.handle(new HttpFrameImpl(type, flags, buff));
    }
  }

  void callHandler(Buffer data) {
    bytesRead += data.length();
    if (decoder != null) {
      try {
        decoder.offer(new DefaultHttpContent(data.getByteBuf()));
      } catch (Exception e) {
        handleException(e);
      }
    }
    if (dataHandler != null) {
      dataHandler.handle(data);
    }
  }

  void callEnd() {
    ended = true;
    conn.reportBytesRead(bytesRead);
    if (decoder != null) {
      try {
        decoder.offer(LastHttpContent.EMPTY_LAST_CONTENT);
        while (decoder.hasNext()) {
          InterfaceHttpData data = decoder.next();
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
        decoder.destroy();
      }
    }
    if (endHandler != null) {
      endHandler.handle(null);
    }
  }

  @Override
  void callReset(long errorCode) {
    ended = true;
    if (exceptionHandler != null) {
      exceptionHandler.handle(new StreamResetException(errorCode));
    }
    response.callReset(errorCode);
    if (endHandler != null) {
      endHandler.handle(null);
    }
  }

  private void checkEnded() {
    if (ended) {
      throw new IllegalStateException("Request has already been read");
    }
  }

  @Override
  public HttpServerRequest exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }

  @Override
  public HttpServerRequest handler(Handler<Buffer> handler) {
    checkEnded();
    dataHandler = handler;
    return this;
  }

  @Override
  public HttpServerRequest pause() {
    doPause();
    return this;
  }

  @Override
  public HttpServerRequest resume() {
    doResume();
    return this;
  }

  @Override
  public HttpServerRequest endHandler(Handler<Void> handler) {
    checkEnded();
    endHandler = handler;
    return this;
  }

  @Override
  public HttpVersion version() {
    return HttpVersion.HTTP_2;
  }

  @Override
  public HttpMethod method() {
    if (method == null) {
      String sMethod = headers.method().toString();
      method = HttpUtils.toVertxMethod(sMethod);
    }
    return method;
  }

  @Override
  public boolean isSSL() {
    // Until we support h2c
    return true;
  }

  @Override
  public String uri() {
    if (uri == null) {
      CharSequence path = headers.path();
      if (path != null) {
        uri = path.toString();
      }
    }
    return uri;
  }

  @Override
  public @Nullable String path() {
    if (path == null) {
      CharSequence path = headers.path();
      if (path != null) {
        this.path = HttpUtils.parsePath(path.toString());
      }
    }
    return path;
  }

  @Override
  public @Nullable String query() {
    if (query == null) {
      CharSequence path = headers.path();
      if (path != null) {
        this.query = HttpUtils.parseQuery(path.toString());
      }
    }
    return query;
  }

  @Override
  public @Nullable String scheme() {
    CharSequence scheme = headers.scheme();
    return scheme != null ? scheme.toString() : null;
  }

  @Override
  public @Nullable String host() {
    CharSequence authority = headers.authority();
    return authority != null ? authority.toString() : null;
  }

  @Override
  public Http2ServerResponseImpl response() {
    return response;
  }

  @Override
  public MultiMap headers() {
    if (headersMap == null) {
      headersMap = new Http2HeadersAdaptor(headers);
    }
    return headersMap;
  }

  @Override
  public @Nullable String getHeader(String headerName) {
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
  public @Nullable String getParam(String paramName) {
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
  public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
    return conn.getPeerCertificateChain();
  }

  @Override
  public String absoluteURI() {
    if (method() == HttpMethod.CONNECT) {
      return null;
    }
    if (absoluteURI == null) {
      try {
        absoluteURI = HttpUtils.absoluteURI(serverOrigin, this);
      } catch (URISyntaxException e) {
        log.error("Failed to create abs uri", e);
      }
    }
    return absoluteURI;
  }

  @Override
  public NetSocket netSocket() {
    checkEnded();
    response.toNetSocket();
    return conn.toNetSocket(this);
  }

  @Override
  public HttpServerRequest setExpectMultipart(boolean expect) {
    checkEnded();
    if (expect) {
      if (decoder == null) {
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
            decoder = new HttpPostRequestDecoder(new NettyFileUploadDataFactory(vertx, this, () -> uploadHandler), req);
          }
        }
      }
    } else {
      decoder = null;
    }
    return this;
  }

  @Override
  public boolean isExpectMultipart() {
    return decoder != null;
  }

  @Override
  public HttpServerRequest uploadHandler(@Nullable Handler<HttpServerFileUpload> handler) {
    checkEnded();
    uploadHandler = handler;
    return this;
  }

  @Override
  public MultiMap formAttributes() {
    // Create it lazily
    if (attributes == null) {
      attributes = new CaseInsensitiveHeaders();
    }
    return attributes;
  }

  @Override
  public @Nullable String getFormAttribute(String attributeName) {
    return formAttributes().get(attributeName);
  }

  @Override
  public ServerWebSocket upgrade() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isEnded() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerRequest unknownFrameHandler(Handler<HttpFrame> handler) {
    unknownFrameHandler = handler;
    return this;
  }

  @Override
  public HttpConnection connection() {
    return conn;
  }

}
