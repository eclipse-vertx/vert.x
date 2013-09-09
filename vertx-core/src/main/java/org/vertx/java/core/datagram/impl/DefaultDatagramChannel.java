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


import io.netty.channel.ChannelFuture;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.datagram.DatagramChannel;
import org.vertx.java.core.datagram.DatagramPacket;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.net.impl.ConnectionBase;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;


/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class DefaultDatagramChannel extends ConnectionBase
        implements DatagramChannel {
  private final io.netty.channel.socket.DatagramChannel channel;
  private Handler<Void> drainHandler;
  private Handler<DatagramPacket> dataHandler;

  DefaultDatagramChannel(VertxInternal vertx, io.netty.channel.socket.DatagramChannel channel, DefaultContext context) {
    super(vertx, channel, context);
    this.channel = channel;
  }

  @Override
  public void close(final Handler<AsyncResult<Void>> handler) {
    channel.close().addListener(new DatagramChannelFutureListener<>(null, handler, vertx, context));
  }

  @SuppressWarnings("unchecked")
  final void addListener(ChannelFuture future, Handler<AsyncResult<DatagramChannel>> handler) {
    if (handler != null) {
      future.addListener(new DatagramChannelFutureListener<>((DatagramChannel) this, handler, vertx, context));
    }
  }


  @Override
  public DatagramChannel joinGroup(InetAddress multicastAddress, Handler<AsyncResult<DatagramChannel>> handler) {
    addListener(channel.joinGroup(multicastAddress), handler);
    return this;
  }

  @Override
  public DatagramChannel joinGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface, Handler<AsyncResult<DatagramChannel>> handler) {
    addListener(channel.joinGroup(multicastAddress, networkInterface), handler);
    return this;
  }

  @Override
  public DatagramChannel joinGroup(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source, Handler<AsyncResult<DatagramChannel>> handler) {
    addListener(channel.joinGroup(multicastAddress, networkInterface, source), handler);
    return this;
  }

  @Override
  public DatagramChannel leaveGroup(InetAddress multicastAddress, Handler<AsyncResult<DatagramChannel>> handler) {
    addListener(channel.leaveGroup(multicastAddress), handler);
    return this;
  }

  @Override
  public DatagramChannel leaveGroup(InetSocketAddress multicastAddress, NetworkInterface networkInterface, Handler<AsyncResult<DatagramChannel>> handler) {
    addListener(channel.leaveGroup(multicastAddress, networkInterface), handler);
    return this;
  }

  @Override
  public DatagramChannel leaveGroup(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source, Handler<AsyncResult<DatagramChannel>> handler) {
    addListener(channel.leaveGroup(multicastAddress, networkInterface, source), handler);
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public DatagramChannel block(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress sourceToBlock, Handler<AsyncResult<DatagramChannel>> handler) {
    addListener(channel.block(multicastAddress, networkInterface, sourceToBlock), handler);
    return  this;
  }

  @Override
  public DatagramChannel block(InetAddress multicastAddress, InetAddress sourceToBlock, Handler<AsyncResult<DatagramChannel>> handler) {
    addListener(channel.block(multicastAddress, sourceToBlock), handler);
    return this;
  }

  public DatagramChannel pause() {
    doPause();
    return this;
  }

  public DatagramChannel resume() {
    doResume();
    return this;
  }


  @Override
  protected void handleInterestedOpsChanged() {
    if (drainHandler != null) {
      drainHandler.handle(null);
    }
  }

  @Override
  public DatagramChannel setWriteQueueMaxSize(int maxSize) {
    doSetWriteQueueMaxSize(maxSize);
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    return doWriteQueueFull();
  }

  @Override
  public DatagramChannel drainHandler(Handler<Void> handler) {
    drainHandler = handler;
    return this;
  }

  @Override
  public DatagramChannel dataHandler(Handler<DatagramPacket> handler) {
    dataHandler = handler;
    return this;
  }

  final void handleMessage(DatagramPacket message) {
    if (dataHandler != null) {
      dataHandler.handle(message);
    }
  }

  @Override
  public DatagramChannel exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }


  @Override
  public DatagramChannel write(String str, InetSocketAddress remote, Handler<AsyncResult<DatagramChannel>> handler) {
    return write(new Buffer(str), remote, handler);
  }

  @Override
  public DatagramChannel write(String str, String enc, InetSocketAddress remote, Handler<AsyncResult<DatagramChannel>> handler) {
    return write(new Buffer(str, enc), remote, handler);
  }

  @Override
  public DatagramChannel write(Buffer packet, InetSocketAddress remote, Handler<AsyncResult<DatagramChannel>> handler) {
    addListener(write(new io.netty.channel.socket.DatagramPacket(packet.getByteBuf(), remote)), handler);
    return this;
  }
}
