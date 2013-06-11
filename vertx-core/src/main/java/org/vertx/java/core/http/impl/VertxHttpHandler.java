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

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.MessageList;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.net.impl.ConnectionBase;
import org.vertx.java.core.net.impl.VertxInboundHandler;

import java.util.Map;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */

public abstract class VertxHttpHandler<C extends ConnectionBase> extends VertxInboundHandler<C> {

  private final VertxInternal vertx;

  protected VertxHttpHandler(VertxInternal vertx, Map<Channel, C> connectionMap) {
    super(vertx, connectionMap);
    this.vertx = vertx;
  }

  @Override
  public void messageReceived(final ChannelHandlerContext chctx, final MessageList<Object> msgs) throws Exception {
    final Channel ch = chctx.channel();
    for (int i = 0; i < msgs.size(); i++) {
      final Object msg = msgs.get(i);

      final C connection = connectionMap.get(ch);
      if (connection != null) {
        final DefaultContext context = getContext(connection);
        // We need to do this since it's possible the server is being used from a worker context
        if (context.isOnCorrectWorker(ch.eventLoop())) {
          try {
            vertx.setContext(context);
            context.startExecute();
            doMessageReceived(connection, chctx, msg);
          } catch (Throwable t) {
            context.reportException(t);
          } finally {
            ByteBufUtil.release(msg);
            context.endExecute();
          }
        } else {
          context.execute(new Runnable() {
            public void run() {
              try {
                doMessageReceived(connection, chctx, msg);
              } catch (Throwable t) {
                context.reportException(t);
              } finally {
                ByteBufUtil.release(msg);
              }
            }
          });
        }
      } else {
        try {
          doMessageReceived(connection, chctx, msg);
        }  catch (Throwable t) {
           chctx.pipeline().fireExceptionCaught(t);
        } finally {
          ByteBufUtil.release(msg);
        }
      }
    }
    msgs.recycle();
  }

  protected abstract void doMessageReceived(C connection, ChannelHandlerContext ctx, Object msg) throws Exception;

}
