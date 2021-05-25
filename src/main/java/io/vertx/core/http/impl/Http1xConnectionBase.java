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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.FileRegion;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.stream.ChunkedFile;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.GoAway;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.WebSocketFrameType;
import io.vertx.core.http.impl.ws.WebSocketFrameImpl;
import io.vertx.core.http.impl.ws.WebSocketFrameInternal;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.impl.ConnectionBase;

import static io.vertx.core.net.impl.VertxHandler.safeBuffer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class Http1xConnectionBase<S extends WebSocketImplBase<S>> extends ConnectionBase implements io.vertx.core.http.HttpConnection {

  protected S webSocket;

  Http1xConnectionBase(ContextInternal context, ChannelHandlerContext chctx) {
    super(context, chctx);
  }

  void handleWsFrame(WebSocketFrame msg) {
    WebSocketFrameInternal frame = decodeFrame(msg);
    WebSocketImplBase<?> w;
    synchronized (this) {
      w = webSocket;
    }
    if (w != null) {
      w.context.execute(frame, w::handleFrame);
    }
  }

  private WebSocketFrameInternal decodeFrame(io.netty.handler.codec.http.websocketx.WebSocketFrame msg) {
    ByteBuf payload = safeBuffer(msg.content());
    boolean isFinal = msg.isFinalFragment();
    WebSocketFrameType frameType;
    if (msg instanceof BinaryWebSocketFrame) {
      frameType = WebSocketFrameType.BINARY;
    } else if (msg instanceof CloseWebSocketFrame) {
      frameType = WebSocketFrameType.CLOSE;
    } else if (msg instanceof PingWebSocketFrame) {
      frameType = WebSocketFrameType.PING;
    } else if (msg instanceof PongWebSocketFrame) {
      frameType = WebSocketFrameType.PONG;
    } else if (msg instanceof TextWebSocketFrame) {
      frameType = WebSocketFrameType.TEXT;
    } else if (msg instanceof ContinuationWebSocketFrame) {
      frameType = WebSocketFrameType.CONTINUATION;
    } else {
      throw new IllegalStateException("Unsupported WebSocket msg " + msg);
    }
    return new WebSocketFrameImpl(frameType, payload, isFinal);
  }

  @Override
  public Future<Void> close() {
    S sock;
    synchronized (this) {
      sock = webSocket;
    }
    if (sock == null) {
      return super.close();
    } else {
      sock.close();
      return closeFuture();
    }
  }

  @Override
  public Http1xConnectionBase closeHandler(Handler<Void> handler) {
    return (Http1xConnectionBase) super.closeHandler(handler);
  }

  @Override
  public Http1xConnectionBase exceptionHandler(Handler<Throwable> handler) {
    return (Http1xConnectionBase) super.exceptionHandler(handler);
  }

  @Override
  public HttpConnection goAway(long errorCode, int lastStreamId, Buffer debugData) {
    throw new UnsupportedOperationException("HTTP/1.x connections don't support GOAWAY");
  }

  @Override
  public HttpConnection goAwayHandler(@Nullable Handler<GoAway> handler) {
    throw new UnsupportedOperationException("HTTP/1.x connections don't support GOAWAY");
  }

  @Override
  public HttpConnection shutdownHandler(@Nullable Handler<Void> handler) {
    throw new UnsupportedOperationException("HTTP/1.x connections cannot be shutdown");
  }

  @Override
  public void shutdown(long timeout, Handler<AsyncResult<Void>> handler) {
    throw new UnsupportedOperationException("HTTP/1.x connections cannot be shutdown");
  }

  @Override
  public Future<Void> shutdown(long timeoutMs) {
    throw new UnsupportedOperationException("HTTP/1.x connections cannot be shutdown");
  }

  @Override
  public Http2Settings settings() {
    throw new UnsupportedOperationException("HTTP/1.x connections don't support SETTINGS");
  }

  @Override
  public Future<Void> updateSettings(Http2Settings settings) {
    throw new UnsupportedOperationException("HTTP/1.x connections don't support SETTINGS");
  }

  @Override
  public HttpConnection updateSettings(Http2Settings settings, Handler<AsyncResult<Void>> completionHandler) {
    throw new UnsupportedOperationException("HTTP/1.x connections don't support SETTINGS");
  }

  @Override
  public Http2Settings remoteSettings() {
    throw new UnsupportedOperationException("HTTP/1.x connections don't support SETTINGS");
  }

  @Override
  public HttpConnection remoteSettingsHandler(Handler<Http2Settings> handler) {
    throw new UnsupportedOperationException("HTTP/1.x connections don't support SETTINGS");
  }

  @Override
  public HttpConnection ping(Buffer data, Handler<AsyncResult<Buffer>> pongHandler) {
    throw new UnsupportedOperationException("HTTP/1.x connections don't support PING");
  }

  @Override
  public HttpConnection pingHandler(@Nullable Handler<Buffer> handler) {
    throw new UnsupportedOperationException("HTTP/1.x connections don't support PING");
  }

  @Override
  public Future<Buffer> ping(Buffer data) {
    throw new UnsupportedOperationException("HTTP/1.x connections don't support PING");
  }

  @Override
  protected void reportsBytesWritten(Object msg) {
    long size = sizeOf(msg);
    reportBytesWritten(size);
  }

  @Override
  protected void reportBytesRead(Object msg) {
    long size = sizeOf(msg);
    reportBytesRead(size);
  }

  static long sizeOf(WebSocketFrame obj) {
    return obj.content().readableBytes();
  }

  static long sizeOf(Object obj) {
    if (obj instanceof Buffer) {
      return ((Buffer) obj).length();
    } else if (obj instanceof ByteBuf) {
      return ((ByteBuf) obj).readableBytes();
    } else if (obj instanceof HttpContent) {
      return ((HttpContent) obj).content().readableBytes();
    } else if (obj instanceof WebSocketFrame) {
      return sizeOf((WebSocketFrame) obj);
    } else if (obj instanceof FileRegion) {
      return ((FileRegion) obj).count();
    } else if (obj instanceof ChunkedFile) {
      ChunkedFile file = (ChunkedFile) obj;
      return file.endOffset() - file.startOffset();
    } else {
      return 0L;
    }
  }
}
