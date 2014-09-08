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
package org.vertx.java.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.vertx.java.core.http.impl.ws.DefaultWebSocketFrame;
import org.vertx.java.core.http.impl.ws.WebSocketFrameInternal;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.net.impl.ConnectionBase;
import org.vertx.java.core.net.impl.VertxHandler;

import java.util.Map;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class VertxHttpHandler<C extends ConnectionBase> extends VertxHandler<C> {
  private final VertxInternal vertx;

  protected VertxHttpHandler(VertxInternal vertx, Map<Channel, C> connectionMap) {
    super(vertx, connectionMap);
    this.vertx = vertx;
  }

  private static ByteBuf safeBuffer(ByteBufHolder holder, ByteBufAllocator allocator) {
    return safeBuffer(holder.content(), allocator);
  }

  @Override
  protected void channelRead(final C connection, final DefaultContext context, final ChannelHandlerContext chctx, final Object msg) throws Exception {
    if (msg instanceof HttpObject) {
        DecoderResult result = ((HttpObject) msg).getDecoderResult();
        if (result.isFailure()) {
            chctx.pipeline().fireExceptionCaught(result.cause());
            return;
        }
    }
    if (connection != null) {
      // we are reading from the channel
      Channel ch = chctx.channel();
      // We need to do this since it's possible the server is being used from a worker context
      if (context.isOnCorrectWorker(ch.eventLoop())) {
        try {
          vertx.setContext(context);
          doMessageReceived(connection, chctx, msg);
        } catch (Throwable t) {
          context.reportException(t);
        }
      } else {
        context.execute(new Runnable() {
          public void run() {
            try {
              doMessageReceived(connection, chctx, msg);
            } catch (Throwable t) {
              context.reportException(t);
            }
          }
        });
      }
    } else {
      try {
        doMessageReceived(connection, chctx, msg);
      }  catch (Throwable t) {
        chctx.pipeline().fireExceptionCaught(t);
      }
    }
  }

  @Override
  protected Object safeObject(Object msg, ByteBufAllocator allocator) throws Exception {
    if (msg instanceof HttpContent) {
      HttpContent content = (HttpContent) msg;
      ByteBuf buf = content.content();
      if (buf != Unpooled.EMPTY_BUFFER && buf.isDirect()) {
        ByteBuf newBuf = safeBuffer(content, allocator);
        if (msg instanceof LastHttpContent) {
          LastHttpContent last = (LastHttpContent) msg;
          return new AssembledLastHttpContent(newBuf, last.trailingHeaders(), last.getDecoderResult());
        } else {
          return new DefaultHttpContent(newBuf);
        }
      }
    } else if (msg instanceof WebSocketFrame) {
      ByteBuf payload = safeBuffer((WebSocketFrame) msg, allocator);
      boolean isFinal = ((WebSocketFrame) msg).isFinalFragment();
        org.vertx.java.core.http.WebSocketFrame.FrameType frameType;
      if (msg instanceof BinaryWebSocketFrame) {
        frameType = org.vertx.java.core.http.WebSocketFrame.FrameType.BINARY;
      } else if (msg instanceof CloseWebSocketFrame) {
        frameType = org.vertx.java.core.http.WebSocketFrame.FrameType.CLOSE;
      } else if (msg instanceof PingWebSocketFrame) {
        frameType = org.vertx.java.core.http.WebSocketFrame.FrameType.PING;
      } else if (msg instanceof PongWebSocketFrame) {
        frameType = org.vertx.java.core.http.WebSocketFrame.FrameType.PONG;
      } else if (msg instanceof TextWebSocketFrame) {
        frameType = org.vertx.java.core.http.WebSocketFrame.FrameType.TEXT;
      } else if (msg instanceof ContinuationWebSocketFrame) {
        frameType = org.vertx.java.core.http.WebSocketFrame.FrameType.CONTINUATION;
      } else {
        throw new IllegalStateException("Unsupported websocket msg " + msg);
      }
      return new DefaultWebSocketFrame(frameType, payload, isFinal);
    }
    return msg;
  }

  
  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
    if (msg instanceof WebSocketFrameInternal) {
      WebSocketFrameInternal frame = (WebSocketFrameInternal) msg;
      ByteBuf buf = frame.getBinaryData();
      if (buf != Unpooled.EMPTY_BUFFER) {
         buf = safeBuffer(buf, ctx.alloc());
      }
      switch (frame.type()) {
      case BINARY:
        msg = new BinaryWebSocketFrame(frame.isFinalFrame(), 0, buf);
        break;
      case TEXT:
        msg = new TextWebSocketFrame(frame.isFinalFrame(), 0, buf);
        break;
      case CLOSE:
        msg = new CloseWebSocketFrame(true, 0, buf);
        break;
      case CONTINUATION:
        msg = new ContinuationWebSocketFrame(frame.isFinalFrame(), 0, buf);
        break;
      case PONG:
        msg = new PongWebSocketFrame(frame.isFinalFrame(), 0, buf);
        break;
      case PING:
        msg = new PingWebSocketFrame(frame.isFinalFrame(), 0, buf);
        break;
      default:
        throw new IllegalStateException("Unsupported websocket msg " + msg);
      }
    }
    ctx.write(msg, promise);
  }

  protected abstract void doMessageReceived(C connection, ChannelHandlerContext ctx, Object msg) throws Exception;

}
