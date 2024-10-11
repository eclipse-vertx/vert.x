/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.impl.future.FutureImpl;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.VertxHandler;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.vertx.core.spi.metrics.Metrics.METRICS_ENABLED;

/**
 * WebSocket handshaker.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ServerWebSocketHandshaker extends FutureImpl<ServerWebSocket> implements ServerWebSocketHandshake, ServerWebSocket {

  private final Http1xServerRequest request;
  private final HttpServerOptions options;
  private final WebSocketServerHandshaker handshaker;
  private boolean done;

  public ServerWebSocketHandshaker(Http1xServerRequest request, WebSocketServerHandshaker handshaker, HttpServerOptions options) {
    super(request.context);
    this.request = request;
    this.handshaker = handshaker;
    this.options = options;
  }

  @Override
  public @Nullable String scheme() {
    return request.scheme();
  }

  @Override
  public @Nullable HostAndPort authority() {
    return request.authority();
  }

  @Override
  public String uri() {
    return request.uri();
  }

  @Override
  public String path() {
    return request.path();
  }

  @Override
  public String query() {
    return request.query();
  }

  @Override
  public Future<ServerWebSocket> accept() {
    synchronized (this) {
      if (done) {
        throw new IllegalStateException();
      }
      done = true;
    }
    ServerWebSocket ws;
    try {
      ws = acceptHandshake();
    } catch (Exception e) {
      return rejectHandshake(BAD_REQUEST.code())
        .transform(ar -> {
          if (ar.succeeded()) {
            return request.context.failedFuture(e);
          } else {
            // result is null
            return (Future) ar;
          }
        });
    }
    tryComplete(ws);
    return this;
  }

  @Override
  public Future<Void> reject(int sc) {
    // Check SC is valid
    synchronized (this) {
      if (done) {
        throw new IllegalStateException();
      }
      done = true;
    }
    tryFail(new RejectedExecutionException()); // Not great but for now OK
    return rejectHandshake(sc);
  }

  @Override
  public MultiMap headers() {
    return request.headers();
  }

  @Override
  public SocketAddress remoteAddress() {
    return request.remoteAddress();
  }

  @Override
  public SocketAddress localAddress() {
    return request.localAddress();
  }

  @Override
  public boolean isSsl() {
    return request.isSSL();
  }

  @Override
  public SSLSession sslSession() {
    return request.sslSession();
  }

  @Override
  public List<Certificate> peerCertificates() throws SSLPeerUnverifiedException {
    return Arrays.asList(sslSession().getPeerCertificates());
  }

  private Future<Void> rejectHandshake(int sc) {
    HttpResponseStatus status = HttpResponseStatus.valueOf(sc);
    Http1xServerResponse response = request.response();
    return response.setStatusCode(sc).end(status.reasonPhrase());
  }

  private ServerWebSocket acceptHandshake() {
    Http1xServerConnection httpConn = (Http1xServerConnection) request.connection();
    ChannelHandlerContext chctx = httpConn.channelHandlerContext();
    Channel channel = chctx.channel();
    Http1xServerResponse response = request.response();
    Object requestMetric = request.metric;
    handshaker.handshake(channel, request.nettyRequest(), (HttpHeaders) response.headers(), channel.newPromise());
    response.completeHandshake();
    // remove compressor as it's not needed anymore once connection was upgraded to websockets
    ChannelPipeline pipeline = channel.pipeline();
    ChannelHandler compressor = pipeline.get(HttpChunkContentCompressor.class);
    if (compressor != null) {
      pipeline.remove(compressor);
    }
    VertxHandler<WebSocketConnectionImpl> handler = VertxHandler.create(ctx -> {
      long closingTimeoutMS = options.getWebSocketClosingTimeout() >= 0 ? options.getWebSocketClosingTimeout() * 1000L : 0L;
      WebSocketConnectionImpl webSocketConn = new WebSocketConnectionImpl(request.context, ctx, true, closingTimeoutMS,httpConn.metrics);
      ServerWebSocketImpl webSocket = new ServerWebSocketImpl(
        (ContextInternal) request.context(),
        webSocketConn,
        handshaker.version() != WebSocketVersion.V00,
        request,
        options.getMaxWebSocketFrameSize(),
        options.getMaxWebSocketMessageSize(),
        options.isRegisterWebSocketWriteHandlers());
      String subprotocol = handshaker.selectedSubprotocol();
      webSocket.subProtocol(subprotocol);
      webSocketConn.webSocket(webSocket);
      webSocketConn.metric(webSocketConn.metric());
      return webSocketConn;
    });
    CompletableFuture<Void> latch = new CompletableFuture<>();
    httpConn.context().execute(() -> {
      // Must be done on event-loop
      pipeline.replace(VertxHandler.class, "handler", handler);
      latch.complete(null);
    });
    // This should actually only block the thread on a worker thread
    try {
      latch.get(10, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    ServerWebSocketImpl webSocket = (ServerWebSocketImpl) handler.getConnection().webSocket();
    if (METRICS_ENABLED && httpConn.metrics != null) {
      webSocket.setMetric(httpConn.metrics.connected(httpConn.metric(), requestMetric, this));
    }
    webSocket.registerHandler(httpConn.context().owner().eventBus());
    return webSocket;
  }

  @Override
  public ServerWebSocket exceptionHandler(Handler<Throwable> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ServerWebSocket handler(Handler<Buffer> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ServerWebSocket pause() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ServerWebSocket fetch(long amount) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ServerWebSocket endHandler(Handler<Void> endHandler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ServerWebSocket setWriteQueueMaxSize(int maxSize) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ServerWebSocket drainHandler(Handler<Void> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ServerWebSocket closeHandler(Handler<Void> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ServerWebSocket frameHandler(Handler<WebSocketFrame> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public WebSocket shutdownHandler(Handler<Void> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public WebSocket textMessageHandler(@Nullable Handler<String> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public WebSocket binaryMessageHandler(@Nullable Handler<Buffer> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public WebSocket pongHandler(@Nullable Handler<Buffer> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String binaryHandlerID() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String textHandlerID() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String subProtocol() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Short closeStatusCode() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String closeReason() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Future<Void> writeFrame(WebSocketFrame frame) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Future<Void> writeFinalTextFrame(String text) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Future<Void> writeFinalBinaryFrame(Buffer data) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Future<Void> writeBinaryMessage(Buffer data) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Future<Void> writeTextMessage(String text) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Future<Void> writePing(Buffer data) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Future<Void> writePong(Buffer data) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Future<Void> end() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Future<Void> shutdown(long timeout, TimeUnit unit, short statusCode, @Nullable String reason) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isClosed() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Future<Void> write(Buffer data) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean writeQueueFull() {
    throw new UnsupportedOperationException();
  }
}
