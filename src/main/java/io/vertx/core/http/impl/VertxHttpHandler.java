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
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.*;
import io.vertx.core.http.impl.ws.WebSocketFrameImpl;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.net.impl.VertxHandler;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class VertxHttpHandler<C extends ConnectionBase> extends VertxHandler<C> {

  private static ByteBuf safeBuffer(ByteBufHolder holder, ByteBufAllocator allocator) {
    return safeBuffer(holder.content(), allocator);
  }

  @Override
  protected Object decode(Object msg, ByteBufAllocator allocator) throws Exception {
    if (msg instanceof HttpContent) {
      HttpContent content = (HttpContent) msg;
      ByteBuf buf = content.content();
      if (buf != Unpooled.EMPTY_BUFFER && buf.isDirect()) {
        ByteBuf newBuf = safeBuffer(content, allocator);
        if (msg instanceof LastHttpContent) {
          LastHttpContent last = (LastHttpContent) msg;
          return new AssembledLastHttpContent(newBuf, last.trailingHeaders(), last.decoderResult());
        } else {
          return new DefaultHttpContent(newBuf);
        }
      }
    } else if (msg instanceof WebSocketFrame) {
      ByteBuf payload = safeBuffer((WebSocketFrame) msg, allocator);
      boolean isFinal = ((WebSocketFrame) msg).isFinalFragment();
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
    return msg;
  }

}
