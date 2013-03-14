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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelStateHandlerAdapter;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.FlowControlStateEvent;

import java.util.Map;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class VertxStateHandler<C extends ConnectionBase> extends ChannelStateHandlerAdapter {
  protected final Map<Channel, C> connectionMap;
  protected VertxStateHandler(Map<Channel, C> connectionMap) {
    this.connectionMap = connectionMap;
  }

  protected Context getContext(C connection) {
    return connection.getContext();
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object obj) throws Exception {
    if (obj instanceof FlowControlStateEvent) {
      FlowControlStateEvent evt = (FlowControlStateEvent) obj;
      final Channel ch = ctx.channel();
      final C conn = connectionMap.get(ch);
      if (conn != null) {
        conn.setWritable(evt.isWritable());
        Context context = getContext(conn);
        if (context.isOnCorrectWorker(ch.eventLoop())) {
          conn.handleInterestedOpsChanged();
        } else {
          context.execute(new Runnable() {
            public void run() {
              conn.handleInterestedOpsChanged();
            }
          });
        }
      }
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext chctx, final Throwable t) throws Exception {
    final Channel ch = chctx.channel();
    final C sock = connectionMap.remove(ch);
    if (sock != null && t instanceof Exception) {
      Context context = getContext(sock);
      if (context.isOnCorrectWorker(ch.eventLoop())) {
        sock.handleException((Exception) t);
        ch.close();
      } else {
        context.execute(new Runnable() {
          public void run() {
            sock.handleException((Exception) t);
            ch.close();
          }
        });
      }
    } else {
      // Ignore - any exceptions before a channel exists will be passed manually via the failed(...) method
    }
  }

  @Override
  public void channelInactive(ChannelHandlerContext chctx) throws Exception {
    final Channel ch = chctx.channel();
    final C sock = connectionMap.remove(ch);
    if (sock != null) {
      Context context = getContext(sock);

      if (context.isOnCorrectWorker(ch.eventLoop())) {
        sock.handleClosed();
      } else {
        context.execute(new Runnable() {
          public void run() {
            sock.handleClosed();
          }
        });
      }
    }
  }
}
