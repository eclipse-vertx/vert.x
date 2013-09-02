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
package org.vertx.java.core.datagram.impl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.datagram.DatagramChannel;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.DefaultFutureResult;
import org.vertx.java.core.impl.VertxInternal;


/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class DatagramChannelFutureListener<T extends DatagramChannel> implements ChannelFutureListener {
  private final Handler<AsyncResult<T>> handler;
  private final T socket;
  private final DefaultContext context;
  private final VertxInternal vertx;

  DatagramChannelFutureListener(T socket, Handler<AsyncResult<T>> handler, VertxInternal vertx, DefaultContext context) {
    this.handler = handler;
    this.socket = socket;
    this.context = context;
    this.vertx = vertx;
  }

  @Override
  public void operationComplete(final ChannelFuture future) throws Exception {
    Channel ch = future.channel();
    if (context.isOnCorrectWorker(ch.eventLoop())) {
      try {
        vertx.setContext(context);
        notifyHandler(future);
      } catch (Throwable t) {
        context.reportException(t);
      }
    } else {
      context.execute(new Runnable() {
        public void run() {
          notifyHandler(future);
        }
      });
    }

  }

  private void notifyHandler(ChannelFuture future) {
    if (future.isSuccess()) {
      handler.handle(new DefaultFutureResult<>(socket));
    } else {
      handler.handle(new DefaultFutureResult<T>(future.cause()));
    }
  }
}
