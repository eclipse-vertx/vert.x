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

import io.netty.channel.socket.DatagramChannel;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.datagram.DatagramEndpoint;
import org.vertx.java.core.datagram.DatagramPacket;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.VertxInternal;

import java.net.InetSocketAddress;


/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class DefaultDatagramEndpoint extends AbstractDatagramChannel<DatagramEndpoint> implements DatagramEndpoint{

  private Handler<DatagramPacket> packetHandler;

  DefaultDatagramEndpoint(VertxInternal vertx, DatagramChannel channel, DefaultContext context) {
    super(vertx, channel, context);
  }

  @Override
  protected void handleInterestedOpsChanged() {
    throw new UnsupportedOperationException();
  }

  @Override
  public DatagramEndpoint write(Buffer packet, InetSocketAddress remote, Handler<AsyncResult<DatagramEndpoint>> handler) {
    addListener(write(new io.netty.channel.socket.DatagramPacket(packet.getByteBuf(), remote)), handler);
    return this;
  }

  @Override
  public DatagramEndpoint packetHandler(Handler<DatagramPacket> packetHandler) {
    this.packetHandler = packetHandler;
    return this;
  }

  @Override
  public DatagramEndpoint exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }


  void handleData(DatagramPacket data) {
    if (packetHandler != null) {
      packetHandler.handle(data);
    }
  }
}
