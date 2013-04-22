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

import io.netty.buffer.BufUtil;
import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelHandlerUtil;
import io.netty.channel.ChannelInboundMessageHandler;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.net.impl.ConnectionBase;
import org.vertx.java.core.net.impl.VertxStateHandler;

import java.util.Map;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */

public abstract class VertxHttpHandler<C extends ConnectionBase> extends VertxStateHandler<C> implements
                      ChannelHandlerUtil.SingleInboundMessageHandler<Object>, ChannelInboundMessageHandler<Object> {

  private final VertxInternal vertx;

  protected VertxHttpHandler(VertxInternal vertx, Map<Channel, C> connectionMap) {
    super(vertx, connectionMap);
    this.vertx = vertx;
  }

  @Override
  public MessageBuf<Object> newInboundBuffer(ChannelHandlerContext channelHandlerContext) throws Exception {
    return Unpooled.messageBuffer();
  }

  @Override
  public void freeInboundBuffer(ChannelHandlerContext channelHandlerContext) throws Exception {
    channelHandlerContext.inboundMessageBuffer().release();
  }

  @Override
  public void inboundBufferUpdated(ChannelHandlerContext channelHandlerContext) throws Exception {
    ChannelHandlerUtil.handleInboundBufferUpdated(channelHandlerContext, this);
  }

  @Override
  public boolean acceptInboundMessage(Object o) throws Exception {
    return true;
  }

  @Override
  public boolean beginMessageReceived(ChannelHandlerContext channelHandlerContext) throws Exception {
    return true;
  }

  @Override
  public void endMessageReceived(ChannelHandlerContext channelHandlerContext) throws Exception {
    // NOOP
  }


  @Override
  public void messageReceived(final ChannelHandlerContext chctx, final Object msg) throws Exception {
    final Channel ch = chctx.channel();

    final C connection = connectionMap.get(ch);
    if (connection != null) {
      DefaultContext context = getContext(connection);
      // We need to do this since it's possible the server is being used from a worker context
      if (context.isOnCorrectWorker(ch.eventLoop())) {
        try {
          vertx.setContext(context);
          doMessageReceived(connection, chctx, msg);
        } catch (Throwable t) {
          context.reportException(t);
        }
      } else {
        BufUtil.retain(msg);
        context.execute(new Runnable() {
          public void run() {
            try {
              doMessageReceived(connection, chctx, msg);
            } catch (Exception e) {
              ch.pipeline().fireExceptionCaught(e);
            } finally {
              BufUtil.release(msg);
            }
         }
        });
      }
    } else {
      doMessageReceived(connection, chctx, msg);
    }
  }

  protected abstract void doMessageReceived(C connection, ChannelHandlerContext ctx, Object msg) throws Exception;

}
