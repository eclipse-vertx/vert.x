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
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerFileUpload;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2ServerRequestImpl implements HttpServerRequest {

  private static final Object END = new Object(); // Marker

  private final Vertx vertx;
  private final ChannelHandlerContext ctx;
  private final Http2Connection conn;
  private final Http2Stream stream;
  private final Http2ServerResponseImpl response;
  private final Http2Headers headers;
  private MultiMap headersMap;
  private HttpMethod method;
  private String path;

  private Handler<Buffer> dataHandler;
  private Handler<Void> endHandler;
  private boolean paused;
  private ArrayDeque<Object> pending = new ArrayDeque<>(8);

  public Http2ServerRequestImpl(
      Vertx vertx,
      Http2Connection conn,
      Http2Stream stream,
      ChannelHandlerContext ctx,
      Http2ConnectionEncoder encoder,
      int streamId,
      Http2Headers headers) {
    this.vertx = vertx;
    this.conn = conn;
    this.stream = stream;
    this.headers = headers;
    this.ctx = ctx;
    this.response = new Http2ServerResponseImpl(ctx, encoder, streamId);
  }

  void end() {
    if (paused || pending.size() > 0) {
      pending.add(END);
    } else {
      if (endHandler != null) {
        endHandler.handle(null);
      }
    }
  }

  boolean handleData(Buffer data) {
    if (!paused) {
      if (dataHandler != null) {
        if (pending.isEmpty()) {
          dataHandler.handle(data);
          return true;
        } else {
          pending.add(data);
          checkNextTick(null);
        }
      }
    } else {
      pending.add(data);
    }
    return false;
  }

  private void checkNextTick(Void v) {
    if (!paused) {
      Object msg = pending.poll();
      if (msg instanceof Buffer) {
        Buffer buf = (Buffer) msg;
        try {
          boolean windowUpdateSent = conn.local().flowController().consumeBytes(stream, buf.length());
          if (windowUpdateSent) {
            ctx.flush();
          }
        } catch (Http2Exception e) {
          e.printStackTrace();
        }
        dataHandler.handle(buf);
        if (pending.size() > 0) {
          vertx.runOnContext(this::checkNextTick);
        }
      } if (msg == END) {
        if (endHandler != null) {
          endHandler.handle(null);
        }
      }
    }
  }

  @Override
  public HttpServerRequest exceptionHandler(Handler<Throwable> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerRequest handler(Handler<Buffer> handler) {
    dataHandler = handler;
    return this;
  }

  @Override
  public HttpServerRequest pause() {
    paused = true;
    return this;
  }

  @Override
  public HttpServerRequest resume() {
    paused = false;
    checkNextTick(null);
    return this;
  }

  @Override
  public HttpServerRequest endHandler(Handler<Void> handler) {
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
      try {
        method = io.vertx.core.http.HttpMethod.valueOf(sMethod);
      } catch (IllegalArgumentException e) {
        method = HttpMethod.UNKNOWN;
      }
    }
    return method;
  }

  @Override
  public boolean isSSL() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String uri() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String path() {
    if (path == null) {
      path = headers.path().toString();
    }
    return path;
  }

  @Override
  public @Nullable String query() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerResponse response() {
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
    throw new UnsupportedOperationException();
  }

  @Override
  public @Nullable String getParam(String paramName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SocketAddress remoteAddress() {
    throw new UnsupportedOperationException();
  }

  @Override
  public SocketAddress localAddress() {
    throw new UnsupportedOperationException();
  }

  @Override
  public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String absoluteURI() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerRequest bodyHandler(@Nullable Handler<Buffer> bodyHandler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public NetSocket netSocket() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerRequest setExpectMultipart(boolean expect) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isExpectMultipart() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpServerRequest uploadHandler(@Nullable Handler<HttpServerFileUpload> uploadHandler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public MultiMap formAttributes() {
    throw new UnsupportedOperationException();
  }

  @Override
  public @Nullable String getFormAttribute(String attributeName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ServerWebSocket upgrade() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isEnded() {
    throw new UnsupportedOperationException();
  }

}
