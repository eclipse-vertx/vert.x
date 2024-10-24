/*
 * Copyright (c) 2011-2034 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl;

import io.netty.channel.ChannelHandlerContext;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.http.ClientWebSocketInternal;
import io.vertx.core.internal.http.WebSocketInternal;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Client WebSocket implementation
 */
public class ClientWebSocketImpl implements ClientWebSocketInternal {

  private final WebSocketClientImpl client;
  private final AtomicReference<Promise<WebSocket>> connect = new AtomicReference<>();
  private volatile WebSocketInternal ws;
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

  ClientWebSocketImpl(WebSocketClientImpl client) {
    this.client = client;
  }

  @Override
  public Future<WebSocket> connect(WebSocketConnectOptions options) {
    return connect(client.vertx().getOrCreateContext(), options);
  }

  @Override
  public Future<WebSocket> connect(Context context, WebSocketConnectOptions options) {
    return connect((ContextInternal) context, options);
  }

  private Future<WebSocket> connect(ContextInternal context, WebSocketConnectOptions options) {
    Promise<WebSocket> promise = context.promise();
    if (!connect.compareAndSet(null, promise)) {
      return context.failedFuture("Already connecting");
    }
    client.webSocket(context, options, promise);
    return promise
      .future()
      .andThen(ar -> {
        if (ar.succeeded()) {
          WebSocketInternal w = (WebSocketInternal) ar.result();
          ws = w;
          w.handler(dataHandler);
          w.binaryMessageHandler(binaryMessageHandler);
          w.textMessageHandler(textMessageHandler);
          w.endHandler(endHandler);
          w.closeHandler(closeHandler);
          w.shutdownHandler(shutdownHandler);
          w.exceptionHandler(exceptionHandler);
          w.drainHandler(drainHandler);
          w.frameHandler(frameHandler);
          w.pongHandler(pongHandler);
          w.resume();
        }
      });
  }

  @Override
  public ClientWebSocket exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    WebSocket w = ws;
    if (w != null) {
      w.exceptionHandler(handler);
    }
    return this;
  }

  @Override
  public ClientWebSocket handler(Handler<Buffer> handler) {
    dataHandler = handler;
    WebSocket w = ws;
    if (w != null) {
      w.handler(handler);
    }
    return this;
  }

  @Override
  public ClientWebSocket pause() {
    delegate().pause();
    return this;
  }

  @Override
  public ClientWebSocket fetch(long amount) {
    delegate().fetch(amount);
    return this;
  }

  @Override
  public ClientWebSocket endHandler(Handler<Void> handler) {
    endHandler = handler;
    WebSocket w = ws;
    if (w != null) {
      w.endHandler(handler);
    }
    return this;
  }

  @Override
  public ChannelHandlerContext channelHandlerContext() {
    return delegate().channelHandlerContext();
  }

  @Override
  public ClientWebSocket setWriteQueueMaxSize(int maxSize) {
    delegate().setWriteQueueMaxSize(maxSize);
    return this;
  }

  @Override
  public ClientWebSocket drainHandler(Handler<Void> handler) {
    drainHandler = handler;
    WebSocket w = ws;
    if (w != null) {
      w.drainHandler(handler);
    }
    return this;
  }

  @Override
  public ClientWebSocket closeHandler(Handler<Void> handler) {
    closeHandler = handler;
    WebSocket w = ws;
    if (w != null) {
      w.closeHandler(handler);
    }
    return this;
  }

  @Override
  public ClientWebSocket shutdownHandler(Handler<Void> handler) {
    shutdownHandler = handler;
    WebSocket w = ws;
    if (w != null) {
      w.shutdownHandler(handler);
    }
    return this;
  }

  @Override
  public ClientWebSocket frameHandler(Handler<WebSocketFrame> handler) {
    frameHandler = handler;
    WebSocket w = ws;
    if (w != null) {
      w.frameHandler(handler);
    }
    return this;
  }

  @Override
  public String binaryHandlerID() {
    return delegate().binaryHandlerID();
  }

  @Override
  public String textHandlerID() {
    return delegate().textHandlerID();
  }

  @Override
  public String subProtocol() {
    return delegate().subProtocol();
  }

  @Override
  public Short closeStatusCode() {
    return delegate().closeStatusCode();
  }

  @Override
  public String closeReason() {
    return delegate().closeReason();
  }

  @Override
  public MultiMap headers() {
    return delegate().headers();
  }

  @Override
  public Future<Void> writeFrame(WebSocketFrame frame) {
    return delegate().writeFrame(frame);
  }

  @Override
  public Future<Void> writeFinalTextFrame(String text) {
    return delegate().writeFinalTextFrame(text);
  }

  @Override
  public Future<Void> writeFinalBinaryFrame(Buffer data) {
    return delegate().writeFinalBinaryFrame(data);
  }

  @Override
  public Future<Void> writeBinaryMessage(Buffer data) {
    return delegate().writeBinaryMessage(data);
  }

  @Override
  public Future<Void> writeTextMessage(String text) {
    return delegate().writeTextMessage(text);
  }

  @Override
  public Future<Void> writePing(Buffer data) {
    return delegate().writePing(data);
  }

  @Override
  public Future<Void> writePong(Buffer data) {
    return delegate().writePong(data);
  }

  @Override
  public ClientWebSocket textMessageHandler(@Nullable Handler<String> handler) {
    textMessageHandler = handler;
    WebSocket w = ws;
    if (w != null) {
      w.textMessageHandler(handler);
    }
    return this;
  }

  @Override
  public ClientWebSocket binaryMessageHandler(@Nullable Handler<Buffer> handler) {
    binaryMessageHandler = handler;
    WebSocket w = ws;
    if (w != null) {
      w.binaryMessageHandler(handler);
    }
    return this;
  }

  @Override
  public ClientWebSocket pongHandler(@Nullable Handler<Buffer> handler) {
    pongHandler = handler;
    WebSocket w = ws;
    if (w != null) {
      w.pongHandler(handler);
    }
    return this;
  }

  @Override
  public Future<Void> end() {
    return delegate().end();
  }

  @Override
  public Future<Void> shutdown(long timeout, TimeUnit unit, short statusCode, @Nullable String reason) {
    return delegate().close(statusCode, reason);
  }

  @Override
  public SocketAddress remoteAddress() {
    return delegate().remoteAddress();
  }

  @Override
  public SocketAddress localAddress() {
    return delegate().localAddress();
  }

  @Override
  public boolean isSsl() {
    return delegate().isSsl();
  }

  @Override
  public boolean isClosed() {
    return delegate().isClosed();
  }

  @Override
  public SSLSession sslSession() {
    return delegate().sslSession();
  }

  @Override
  public List<Certificate> peerCertificates() throws SSLPeerUnverifiedException {
    return delegate().peerCertificates();
  }

  @Override
  public Future<Void> write(Buffer data) {
    return delegate().write(data);
  }

  @Override
  public boolean writeQueueFull() {
    return delegate().writeQueueFull();
  }

  private WebSocketInternal delegate() {
    WebSocketInternal w = ws;
    if (w == null) {
      throw new IllegalStateException("Not connected");
    }
    return w;
  }
}
