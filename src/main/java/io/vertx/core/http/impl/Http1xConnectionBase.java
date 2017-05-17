/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.vertx.core.http.impl.ws.WebSocketFrameInternal;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.impl.ConnectionBase;

import static io.vertx.core.http.impl.Http2ConnectionBase.safeBuffer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class Http1xConnectionBase extends ConnectionBase {

  Http1xConnectionBase(VertxInternal vertx, ChannelHandlerContext chctx, ContextImpl context) {
    super(vertx, chctx, context);
  }

  @Override
  protected Object encode(Object obj) {
    if (obj instanceof WebSocketFrameInternal) {
      WebSocketFrameInternal frame = (WebSocketFrameInternal) obj;
      ByteBuf buf = frame.getBinaryData();
      if (buf != Unpooled.EMPTY_BUFFER) {
        buf = safeBuffer(buf, chctx.alloc());
      }
      switch (frame.type()) {
        case BINARY:
          obj = new BinaryWebSocketFrame(frame.isFinal(), 0, buf);
          break;
        case TEXT:
          obj = new TextWebSocketFrame(frame.isFinal(), 0, buf);
          break;
        case CLOSE:
          obj = new CloseWebSocketFrame(true, 0, buf);
          break;
        case CONTINUATION:
          obj = new ContinuationWebSocketFrame(frame.isFinal(), 0, buf);
          break;
        case PONG:
          obj = new PongWebSocketFrame(buf);
          break;
        case PING:
          obj = new PingWebSocketFrame(buf);
          break;
        default:
          throw new IllegalStateException("Unsupported websocket msg " + obj);
      }
    }
    return super.encode(obj);
  }
}
