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
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.VertxHandler;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.vertx.core.spi.metrics.Metrics.METRICS_ENABLED;

/**
 * Implementation that models a proxies a lazy {@link ServerWebSocket} since the API allows to reject a WebSocket
 * handshake.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ServerWebSocketHandshaker implements ServerWebSocket {

  private static final int ST_PENDING = 0, ST_ACCEPTED = 1, ST_REJECTED = 2;

  private Http1xServerRequest request;
  private HttpServerOptions options;
  private WebSocketServerHandshaker handshaker;
  private int status;
  private ServerWebSocket webSocket;
  private Future<Integer> futureHandshake;
  private Handler<Throwable> exceptionHandler;
  private Handler<Buffer> dataHandler;
  private Handler<Void> endHandler;
  private Handler<Void> closeHandler;
  private Handler<Void> shutdownHandler;
  private Handler<Void> drainHandler;
  private Handler<WebSocketFrame> frameHandler;
  private Handler<String> textMessageHandler;
  private Handler<Buffer> binaryMessageHandler;
  private Handler<Buffer> pongHandler;

  public ServerWebSocketHandshaker(Http1xServerRequest request, WebSocketServerHandshaker handshaker, HttpServerOptions options) {
    this.request = request;
    this.handshaker = handshaker;
    this.options = options;
    this.status = ST_PENDING;
  }

  @Override
  public @Nullable String scheme() {
    Http1xServerRequest r = request;
    if (r != null) {
      return r.scheme();
    } else {
      return webSocket.scheme();
    }
  }

  @Override
  public @Nullable HostAndPort authority() {
    Http1xServerRequest r = request;
    if (r != null) {
      return r.authority();
    } else {
      return webSocket.authority();
    }
  }

  @Override
  public String uri() {
    Http1xServerRequest r = request;
    if (r != null) {
      return r.uri();
    } else {
      return webSocket.uri();
    }
  }

  @Override
  public String path() {
    Http1xServerRequest r = request;
    if (r != null) {
      return r.path();
    } else {
      return webSocket.path();
    }
  }

  @Override
  public @Nullable String query() {
    Http1xServerRequest r = request;
    if (r != null) {
      return r.query();
    } else {
      return webSocket.query();
    }
  }

  @Override
  public void accept() {
    webSocketOrDie();
  }

  void tryAccept() {
    resolveWebSocket();
  }

  @Override
  public void reject(int sc) {
    // Check SC is valid
    synchronized (this) {
        if (status == ST_PENDING) {
            status = ST_REJECTED;
        } else {
            throw new IllegalStateException();
        }
    }
    rejectHandshake(sc);
  }

  @Override
  public Future<Integer> setHandshake(Future<Integer> future) {
    Future<Integer> ret;
    synchronized (this) {
      if (status != ST_PENDING || futureHandshake != null) {
        throw new IllegalStateException();
      }
      ret = future.andThen(ar -> {
        if (ar.succeeded()) {
          int sc = ar.result();
          if (sc == 101) {
            synchronized (this) {
              futureHandshake = null;
              accept();
            }
          } else {
            synchronized (this) {
              status = ST_REJECTED;
            }
            reject(sc);
          }
        }
      });
      futureHandshake = ret;
    }
    return ret;
  }

  @Override
  public ServerWebSocket exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    WebSocket ws = webSocket;
    if (ws != null) {
      ws.exceptionHandler(handler);
    }
    return this;
  }

  @Override
  public ServerWebSocket handler(Handler<Buffer> handler) {
    dataHandler = handler;
    WebSocket ws = webSocket;
    if (ws != null) {
      ws.handler(handler);
    }
    return this;
  }

  @Override
  public ServerWebSocket pause() {
    webSocketOrDie().pause();
    return this;
  }

  @Override
  public ServerWebSocket fetch(long amount) {
    webSocketOrDie().fetch(amount);
    return this;
  }

  @Override
  public ServerWebSocket endHandler(Handler<Void> handler) {
    endHandler = handler;
    WebSocket ws = webSocket;
    if (ws != null) {
      ws.endHandler(handler);
    }
    return this;
  }

  @Override
  public ServerWebSocket setWriteQueueMaxSize(int maxSize) {
    webSocketOrDie().setWriteQueueMaxSize(maxSize);
    return this;
  }

  @Override
  public ServerWebSocket drainHandler(Handler<Void> handler) {
    drainHandler = handler;
    WebSocket ws = webSocket;
    if (ws != null) {
      ws.drainHandler(handler);
    }
    return this;
  }

  @Override
  public ServerWebSocket closeHandler(Handler<Void> handler) {
    closeHandler = handler;
    WebSocket ws = webSocket;
    if (ws != null) {
      ws.closeHandler(handler);
    }
    return this;
  }

  @Override
  public WebSocket shutdownHandler(Handler<Void> handler) {
    shutdownHandler = handler;
    WebSocket ws = webSocket;
    if (ws != null) {
      ws.shutdownHandler(handler);
    }
    return this;
  }

  @Override
  public ServerWebSocket frameHandler(Handler<WebSocketFrame> handler) {
    frameHandler = handler;
    WebSocket ws = webSocket;
    if (ws != null) {
      ws.frameHandler(handler);
    }
    return this;
  }

  @Override
  public String binaryHandlerID() {
    return webSocketOrDie().binaryHandlerID();
  }

  @Override
  public String textHandlerID() {
    return webSocketOrDie().textHandlerID();
  }

  @Override
  public String subProtocol() {
    ServerWebSocket ws = webSocket;
    if (ws == null) {
      return null;
    } else {
      return ws.subProtocol();
    }
  }

  @Override
  public Short closeStatusCode() {
    return webSocketOrDie().closeStatusCode();
  }

  @Override
  public String closeReason() {
    return webSocketOrDie().closeReason();
  }

  @Override
  public MultiMap headers() {
    return webSocketOrDie().headers();
  }

  @Override
  public Future<Void> writeFrame(WebSocketFrame frame) {
    return webSocketOrDie().writeFrame(frame);
  }

  @Override
  public Future<Void> writeFinalTextFrame(String text) {
    return webSocketOrDie().writeFinalTextFrame(text);
  }

  @Override
  public Future<Void> writeFinalBinaryFrame(Buffer data) {
    return webSocketOrDie().writeFinalBinaryFrame(data);
  }

  @Override
  public Future<Void> writeBinaryMessage(Buffer data) {
    return webSocketOrDie().writeBinaryMessage(data);
  }

  @Override
  public Future<Void> writeTextMessage(String text) {
    return webSocketOrDie().writeTextMessage(text);
  }

  @Override
  public Future<Void> writePing(Buffer data) {
    return webSocketOrDie().writePing(data);
  }

  @Override
  public Future<Void> writePong(Buffer data) {
    return webSocketOrDie().writePong(data);
  }

  @Override
  public ServerWebSocket textMessageHandler(@Nullable Handler<String> handler) {
    textMessageHandler = handler;
    WebSocket ws = webSocket;
    if (ws != null) {
      ws.textMessageHandler(handler);
    }
    return this;
  }

  @Override
  public ServerWebSocket binaryMessageHandler(@Nullable Handler<Buffer> handler) {
    binaryMessageHandler = handler;
    WebSocket ws = webSocket;
    if (ws != null) {
      ws.binaryMessageHandler(handler);
    }
    return this;
  }

  @Override
  public ServerWebSocket pongHandler(@Nullable Handler<Buffer> handler) {
    pongHandler = handler;
    WebSocket ws = webSocket;
    if (ws != null) {
      ws.pongHandler(handler);
    }
    return this;
  }

  @Override
  public Future<Void> end() {
    return webSocketOrDie().end();
  }

  @Override
  public Future<Void> shutdown(long timeout, TimeUnit unit, short statusCode, @Nullable String reason) {
    WebSocket delegate = webSocketOrDie();
    return delegate.shutdown(timeout, unit, statusCode, reason);
  }

  @Override
  public SocketAddress remoteAddress() {
    return webSocketOrDie().remoteAddress();
  }

  @Override
  public SocketAddress localAddress() {
    return webSocketOrDie().localAddress();
  }

  @Override
  public boolean isSsl() {
    return webSocketOrDie().isSsl();
  }

  @Override
  public boolean isClosed() {
    return webSocketOrDie().isClosed();
  }

  @Override
  public SSLSession sslSession() {
    return webSocketOrDie().sslSession();
  }

  @Override
  public List<Certificate> peerCertificates() throws SSLPeerUnverifiedException {
    return webSocketOrDie().peerCertificates();
  }

  @Override
  public Future<Void> write(Buffer data) {
    return webSocketOrDie().write(data);
  }

  @Override
  public boolean writeQueueFull() {
    return webSocketOrDie().writeQueueFull();
  }

  private WebSocket webSocketOrDie() {
    WebSocket ws = resolveWebSocket();
    if (ws == null) {
      throw new IllegalStateException("WebSocket handshake failed");
    }
    return ws;
  }

  private WebSocket resolveWebSocket() {
    boolean reject = false;
    try {
      if (futureHandshake != null) {
        return null;
      }
      synchronized (this) {
        switch (status) {
          case ST_PENDING:
            ServerWebSocket ws;
            try {
              ws = acceptHandshake();
            } catch (Exception e) {
              status = ST_REJECTED;
              reject = true;
              throw e;
            }
            ws.handler(dataHandler);
            ws.binaryMessageHandler(binaryMessageHandler);
            ws.textMessageHandler(textMessageHandler);
            ws.endHandler(endHandler);
            ws.closeHandler(closeHandler);
            ws.shutdownHandler(shutdownHandler);
            ws.exceptionHandler(exceptionHandler);
            ws.drainHandler(drainHandler);
            ws.frameHandler(frameHandler);
            ws.pongHandler(pongHandler);
            status = ST_ACCEPTED;
            webSocket = ws;
            return ws;
          case ST_REJECTED:
            return null;
          case ST_ACCEPTED:
            return webSocket;
          default:
            throw new UnsupportedOperationException();
        }
      }
    } finally {
      if (reject) {
        rejectHandshake(BAD_REQUEST.code());
      }
    }
  }

  private void rejectHandshake(int sc) {
    HttpResponseStatus status = HttpResponseStatus.valueOf(sc);
    Http1xServerResponse response = request.response();
    response.setStatusCode(sc).end(status.reasonPhrase());
  }

  private ServerWebSocket acceptHandshake() {
    Http1xServerConnection httpConn = (Http1xServerConnection) request.connection();
    ChannelHandlerContext chctx = httpConn.channelHandlerContext();
    Channel channel = chctx.channel();
    Http1xServerResponse response = request.response();
    Object requestMetric = request.metric;
    try {
      handshaker.handshake(channel, request.nettyRequest());
    } catch (Exception e) {
      rejectHandshake(BAD_REQUEST.code());
      throw e;
    }
    response.completeHandshake();
    // remove compressor as it's not needed anymore once connection was upgraded to websockets
    ChannelPipeline pipeline = channel.pipeline();
    ChannelHandler compressor = pipeline.get(HttpChunkContentCompressor.class);
    if (compressor != null) {
      pipeline.remove(compressor);
    }
    VertxHandler<WebSocketConnection> handler = VertxHandler.create(ctx -> {
      long closingTimeoutMS = options.getWebSocketClosingTimeout() >= 0 ? options.getWebSocketClosingTimeout() * 1000L : 0L;
      WebSocketConnection webSocketConn = new WebSocketConnection(request.context, ctx, true, closingTimeoutMS,httpConn.metrics);
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
}
