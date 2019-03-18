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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.GoAway;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.impl.ws.WebSocketFrameImpl;
import io.vertx.core.http.impl.ws.WebSocketFrameInternal;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.impl.ChannelFutureListenerAdapter;
import io.vertx.core.net.impl.ConnectionBase;

import static io.vertx.core.net.impl.VertxHandler.safeBuffer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class Http1xConnectionBase<S extends WebSocketImplBase<S>> extends ConnectionBase implements io.vertx.core.http.HttpConnection {

  protected S ws;
  private boolean closeFrameSent;

  Http1xConnectionBase(VertxInternal vertx, ChannelHandlerContext chctx, ContextInternal context) {
    super(vertx, chctx, context);
  }

  WebSocketFrame encodeFrame(WebSocketFrameImpl frame) {
    ByteBuf buf = frame.getBinaryData();
    if (buf != Unpooled.EMPTY_BUFFER) {
      buf = safeBuffer(buf, chctx.alloc());
    }
    switch (frame.type()) {
      case BINARY:
        return new BinaryWebSocketFrame(frame.isFinal(), 0, buf);
      case TEXT:
        return new TextWebSocketFrame(frame.isFinal(), 0, buf);
      case CLOSE:
        return new CloseWebSocketFrame(true, 0, buf);
      case CONTINUATION:
        return new ContinuationWebSocketFrame(frame.isFinal(), 0, buf);
      case PONG:
        return new PongWebSocketFrame(buf);
      case PING:
        return new PingWebSocketFrame(buf);
      default:
        throw new IllegalStateException("Unsupported websocket msg " + frame);
    }
  }

  private WebSocketFrameInternal decodeFrame(WebSocketFrame msg) {
    ByteBuf payload = safeBuffer(msg, chctx.alloc());
    boolean isFinal = msg.isFinalFragment();
    FrameType frameType;
    if (msg instanceof BinaryWebSocketFrame) {
      frameType = FrameType.BINARY;
    } else if (msg instanceof CloseWebSocketFrame) {
      frameType = FrameType.CLOSE;
    } else if (msg instanceof PingWebSocketFrame) {
      frameType = FrameType.PING;
    } else if (msg instanceof PongWebSocketFrame) {
      frameType = FrameType.PONG;
    } else if (msg instanceof TextWebSocketFrame) {
      frameType = FrameType.TEXT;
    } else if (msg instanceof ContinuationWebSocketFrame) {
      frameType = FrameType.CONTINUATION;
    } else {
      throw new IllegalStateException("Unsupported websocket msg " + msg);
    }
    return new WebSocketFrameImpl(frameType, payload, isFinal);
  }

  void handleWsFrame(WebSocketFrame msg) {
    WebSocketFrameInternal frame = decodeFrame(msg);
    S w;
    synchronized (this) {
      switch (frame.type()) {
        case PING:
          // Echo back the content of the PING frame as PONG frame as specified in RFC 6455 Section 5.5.2
          chctx.writeAndFlush(new PongWebSocketFrame(frame.getBinaryData().copy()));
          break;
        case CLOSE:
          synchronized (this) {
            if (!closeFrameSent) {
              // Echo back close frame and close the connection once it was written.
              // This is specified in the WebSockets RFC 6455 Section  5.4.1
              CloseWebSocketFrame closeFrame = new CloseWebSocketFrame(frame.closeStatusCode(), frame.closeReason());
              chctx.writeAndFlush(closeFrame).addListener(ChannelFutureListener.CLOSE);
              closeFrameSent = true;
            }
          }
          break;
      }
      w = ws;
    }
    if (w != null) {
      w.context.dispatch(frame, ((WebSocketImplBase)w)::handleFrame);
    }
  }

  @Override
  public Future<Void> close() {
    Promise<Void> promise = Promise.promise();
    close(promise);
    return promise.future();
  }

  @Override
  public void close(Handler<AsyncResult<Void>> handler) {
    closeWithPayload((short) 1000, null, handler);
  }

  void closeWithPayload(short code, String reason, Handler<AsyncResult<Void>> handler) {
    if (ws == null) {
      super.close(handler);
    } else {
      // make sure everything is flushed out on close
      ByteBuf byteBuf = HttpUtils.generateWSCloseFrameByteBuf(code, reason);
      CloseWebSocketFrame frame = new CloseWebSocketFrame(true, 0, byteBuf);
      ChannelPromise promise = chctx.newPromise();
      flush(promise);
      // close the WebSocket connection by sending a close frame with specified payload.
      promise.addListener((ChannelFutureListener) future -> {
        ChannelFuture fut = chctx.writeAndFlush(frame);
        boolean server = this instanceof Http1xServerConnection;
        if (server) {
          fut.addListener((ChannelFutureListener) f -> {
            ChannelFuture closeFut = chctx.channel().close();
            if (handler != null) {
              closeFut.addListener(new ChannelFutureListenerAdapter<>(context, null, handler));
            }
          });
        } else {
          if (handler != null) {
            fut.addListener(new ChannelFutureListenerAdapter<>(context, null, handler));
          }
        }
      });
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
    throw new UnsupportedOperationException("HTTP/1.x connections don't support GOAWAY");
  }

  @Override
  public HttpConnection shutdown() {
    throw new UnsupportedOperationException("HTTP/1.x connections don't support GOAWAY");
  }

  @Override
  public HttpConnection shutdown(long timeoutMs) {
    throw new UnsupportedOperationException("HTTP/1.x connections don't support GOAWAY");
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
}
