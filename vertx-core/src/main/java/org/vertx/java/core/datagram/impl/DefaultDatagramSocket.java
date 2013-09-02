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

import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.datagram.DatagramSocket;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.DefaultFutureResult;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.net.impl.ConnectionBase;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class DefaultDatagramSocket extends ConnectionBase implements DatagramSocket {

  private final DatagramChannel channel;
  private Handler<Void> drainHandler;
  private Handler<Void> closeHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<org.vertx.java.core.datagram.DatagramPacket> packetHandler;

  DefaultDatagramSocket(VertxInternal vertx, DatagramChannel channel, DefaultContext context) {
    super(vertx, channel, context);
    this.channel = channel;
  }

  @Override
  public DatagramSocket write(Buffer buffer, InetSocketAddress remote, Handler<AsyncResult<DatagramSocket>> handler) {
    write0(new DatagramPacket(buffer.getByteBuf(), remote), handler);
    return this;
  }

  @Override
  public DatagramSocket write(Buffer buffer, Handler<AsyncResult<DatagramSocket>> handler) {
    write0(buffer.getByteBuf(), handler);
    return this;
  }

  private void write0(Object msg, Handler<AsyncResult<DatagramSocket>> handler) {
    addListener(write(msg), handler);
  }

  @Override
  public DatagramSocket connect(InetSocketAddress remote, Handler<AsyncResult<DatagramSocket>> handler) {
    addListener(channel.connect(remote), handler);
    return this;
  }

  @Override
  public DatagramSocket disconnect(Handler<AsyncResult<DatagramSocket>> handler) {
    addListener(channel.disconnect(), handler);
    return this;
  }

  @Override
  public void close(final Handler<AsyncResult<Void>> handler) {
    channel.close().addListener(new ChannelFutureListener() {
      @Override
      public void operationComplete(ChannelFuture future) throws Exception {
        if (future.isSuccess()) {
          handler.handle(null);
        } else {
          handler.handle(new DefaultFutureResult<Void>(future.cause()));
        }
      }
    });
  }

  void addListener(ChannelFuture future, Handler<AsyncResult<DatagramSocket>> handler) {
    future.addListener(new DatagramChannelFutureListener(this, handler, vertx, context));
  }

  @Override
  public boolean isConnected() {
    return channel.isConnected();
  }

  @Override
  public DatagramSocket joinGroup(InetAddress multicastAddress, Handler<AsyncResult<DatagramSocket>> handler) {
    addListener(channel.joinGroup(multicastAddress), handler);
    return this;
  }

  @Override
  public DatagramSocket joinGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface, Handler<AsyncResult<DatagramSocket>> handler) {
    addListener(channel.joinGroup(multicastAddress, networkInterface), handler);
    return this;
  }

  @Override
  public DatagramSocket joinGroup(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source, Handler<AsyncResult<DatagramSocket>> handler) {
    addListener(channel.joinGroup(multicastAddress, networkInterface, source), handler);
    return this;
  }

  @Override
  public DatagramSocket leaveGroup(InetAddress multicastAddress, Handler<AsyncResult<DatagramSocket>> handler) {
    addListener(channel.leaveGroup(multicastAddress), handler);
    return this;
  }

  @Override
  public DatagramSocket leaveGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface, Handler<AsyncResult<DatagramSocket>> handler) {
    addListener(channel.leaveGroup(multicastAddress, networkInterface), handler);
    return this;
  }

  @Override
  public DatagramSocket leaveGroup(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source, Handler<AsyncResult<DatagramSocket>> handler) {
    addListener(channel.leaveGroup(multicastAddress, networkInterface, source), handler);
    return this;
  }

  @Override
  public DatagramSocket block(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress sourceToBlock, Handler<AsyncResult<DatagramSocket>> handler) {
    addListener(channel.block(multicastAddress, networkInterface, sourceToBlock), handler);
    return this;
  }

  @Override
  public DatagramSocket block(InetAddress multicastAddress, InetAddress sourceToBlock, Handler<AsyncResult<DatagramSocket>> handler) {
    addListener(channel.block(multicastAddress, sourceToBlock), handler);
    return this;
  }

  @Override
  protected void handleInterestedOpsChanged() {
    if (drainHandler != null) {
      drainHandler.handle(null);
    }
  }

  @Override
  public DatagramSocket packetHandler(Handler<org.vertx.java.core.datagram.DatagramPacket> handler) {
    packetHandler = handler;
    return this;
  }

  @Override
  public DatagramSocket exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }

  @Override
  public DatagramSocket closeHandler(Handler<Void> handler) {
    closeHandler = handler;
    return this;
  }

  @Override
  public DatagramSocket setWriteQueueMaxSize(int maxSize) {
    doSetWriteQueueMaxSize(maxSize);
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    return doWriteQueueFull();
  }

  @Override
  public DatagramSocket drainHandler(Handler<Void> handler) {
    drainHandler = handler;
    return this;
  }

  void handlePacket(org.vertx.java.core.datagram.DatagramPacket packet) {
    if (packetHandler != null) {
      packetHandler.handle(packet);
    }
  }
}
