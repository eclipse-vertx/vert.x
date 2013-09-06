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

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.socket.DatagramChannel;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.datagram.ConnectedDatagramChannel;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.VertxInternal;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class DefaultConnectedDatagramChannel extends AbstractDatagramChannel<ConnectedDatagramChannel, Buffer> implements ConnectedDatagramChannel {

  private Handler<Void> endHandler;

  DefaultConnectedDatagramChannel(VertxInternal vertx, DatagramChannel channel, DefaultContext context) {
    super(vertx, channel, context);
  }

  @Override
  public ConnectedDatagramChannel write(Buffer buffer) {
    write(buffer.getByteBuf()).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
    return this;
  }

  @Override
  public ConnectedDatagramChannel write(String str) {
    return write(new Buffer(str));
  }

  @Override
  public ConnectedDatagramChannel write(String str, String enc) {
    return write(new Buffer(str, enc));
  }

  @Override
  public ConnectedDatagramChannel endHandler(Handler<Void> handler) {
    endHandler = handler;
    return this;
  }

  @Override
  protected void handleClosed() {
    super.handleClosed();
    if (endHandler != null) {
      setContext();
      try {
        endHandler.handle(null);
      } catch (Throwable t) {
        handleHandlerException(t);
      }
    }
  }
}
