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

package org.vertx.java.core.net.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.MessageList;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.VertxInternal;

import java.util.Map;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class VertxNetHandler extends VertxInboundHandler<DefaultNetSocket> {

  public VertxNetHandler(VertxInternal vertx, Map<Channel, DefaultNetSocket> connectionMap) {
    super(vertx, connectionMap);
  }


  @Override
  public void messageReceived(ChannelHandlerContext chctx, final MessageList<Object> msgs) {
    final DefaultNetSocket sock = connectionMap.get(chctx.channel());
    if (sock != null) {
      Channel ch = chctx.channel();
      final DefaultContext context = getContext(sock);
      final MessageList<ByteBuf> cast = msgs.cast();
      // We need to do this since it's possible the server is being used from a worker context
      if (context.isOnCorrectWorker(ch.eventLoop())) {
        try {
          vertx.setContext(context);
          context.startExecute();

          for (int i = 0; i < cast.size(); i++) {
            try {
              sock.handleDataReceived(new Buffer(cast.get(i)));
            } catch (Throwable t) {
              context.reportException(t);
            }
          }
        } catch (Throwable t) {
          context.reportException(t);
        } finally {
          msgs.releaseAllAndRecycle();
          context.endExecute();
        }
      } else {
        context.execute(new Runnable() {
          public void run() {
            try {
              for (int i = 0; i < cast.size(); i++) {
                try {
                  sock.handleDataReceived(new Buffer(cast.get(i)));
                } catch (Throwable t) {
                  context.reportException(t);
                }
              }
            } finally {
              msgs.releaseAllAndRecycle();
            }
          }
        });
      }
    } else {
      // just discard
      msgs.releaseAllAndRecycle();
    }
  }
}
