/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.vertx.java.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.vertx.java.core.http.impl.ws.*;
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

  private static ByteBuf safeBuffer(ByteBufHolder holder) {
    return safeBuffer(holder.content());
  }

  @Override
  protected void channelRead(final C connection, final DefaultContext context, final ChannelHandlerContext chctx, final Object msg) throws Exception {
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
  protected Object safeObject(Object msg) throws Exception {
    if (msg instanceof HttpContent) {
      HttpContent content = (HttpContent) msg;
      ByteBuf buf = content.content();
      if (buf != Unpooled.EMPTY_BUFFER && buf.isDirect()) {
        ByteBuf newBuf = safeBuffer(content);
        if (msg instanceof LastHttpContent) {
          LastHttpContent last = (LastHttpContent) msg;
          return new AssembledLastHttpContent(newBuf, last.trailingHeaders());
        } else {
          return new DefaultHttpContent(newBuf);
        }
      }
    } else if (msg instanceof WebSocketFrame) {
      ByteBuf payload = safeBuffer((WebSocketFrame) msg);
      if (msg instanceof BinaryWebSocketFrame) {
        return new DefaultWebSocketFrame(org.vertx.java.core.http.impl.ws.WebSocketFrame.FrameType.BINARY, payload);
      } else if (msg instanceof CloseWebSocketFrame) {
        return new DefaultWebSocketFrame(org.vertx.java.core.http.impl.ws.WebSocketFrame.FrameType.CLOSE, payload);
      } else if (msg instanceof PingWebSocketFrame) {
        return new DefaultWebSocketFrame(org.vertx.java.core.http.impl.ws.WebSocketFrame.FrameType.PING, payload);
      } else if (msg instanceof PongWebSocketFrame) {
        return new DefaultWebSocketFrame(org.vertx.java.core.http.impl.ws.WebSocketFrame.FrameType.PONG, payload);
      } else if (msg instanceof TextWebSocketFrame) {
        return new DefaultWebSocketFrame(org.vertx.java.core.http.impl.ws.WebSocketFrame.FrameType.TEXT, payload);
      } else if (msg instanceof ContinuationWebSocketFrame) {
        return new DefaultWebSocketFrame(org.vertx.java.core.http.impl.ws.WebSocketFrame.FrameType.CONTINUATION, payload);
      } else {
        throw new IllegalStateException("Unsupported websocket msg " + msg);
      }
    }
    return msg;
  }


  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
    if (msg instanceof org.vertx.java.core.http.impl.ws.WebSocketFrame) {
      org.vertx.java.core.http.impl.ws.WebSocketFrame frame = (org.vertx.java.core.http.impl.ws.WebSocketFrame) msg;
      ByteBuf buf = ((org.vertx.java.core.http.impl.ws.WebSocketFrame) msg).getBinaryData();
      if (buf != Unpooled.EMPTY_BUFFER) {
         buf = safeBuffer(buf);
      }
      switch (frame.getType()) {
        case BINARY:
          msg = new BinaryWebSocketFrame(buf);
          break;
        case TEXT:
          msg = new TextWebSocketFrame(buf);
          break;
        case CLOSE:
          msg = new CloseWebSocketFrame(true, 0, buf);
          break;
        case CONTINUATION:
          msg = new ContinuationWebSocketFrame(buf);
          break;
        case PONG:
          msg = new PongWebSocketFrame(buf);
          break;
        case PING:
          msg = new PingWebSocketFrame(buf);
          break;
        default:
          throw new IllegalStateException("Unsupported websocket msg " + msg);
      }
    }
    ctx.write(msg, promise);
  }

  protected abstract void doMessageReceived(C connection, ChannelHandlerContext ctx, Object msg) throws Exception;

}
