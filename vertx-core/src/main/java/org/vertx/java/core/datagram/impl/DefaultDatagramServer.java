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
import org.vertx.java.core.datagram.DatagramPacket;
import org.vertx.java.core.datagram.DatagramServer;
import org.vertx.java.core.impl.VertxInternal;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.StandardProtocolFamily;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class DefaultDatagramServer extends AbstractDatagramSupport<DatagramServer> implements DatagramServer {
  private Handler<DatagramPacket> dataHandler;
  public DefaultDatagramServer(VertxInternal vertx, StandardProtocolFamily family) {
    super(vertx, family);
    channel.pipeline().addLast("handler", new DatagramServerHandler(this.vertx, this));
  }

  @Override
  public DatagramServer joinGroup(InetAddress multicastAddress, Handler<AsyncResult<DatagramServer>> handler) {
    configurable = false;
    addListener(channel().joinGroup(multicastAddress), handler);
    return this;
  }

  @Override
  public DatagramServer joinGroup(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source, Handler<AsyncResult<DatagramServer>> handler) {
    configurable = false;
    addListener(channel().joinGroup(multicastAddress, networkInterface, source), handler);
    return this;
  }

  @Override
  public DatagramServer leaveGroup(InetAddress multicastAddress, Handler<AsyncResult<DatagramServer>> handler) {
    configurable = false;
    addListener(channel().leaveGroup(multicastAddress), handler);
    return this;
  }

  @Override
  public DatagramServer leaveGroup(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source, Handler<AsyncResult<DatagramServer>> handler) {
    configurable = false;
    addListener(channel().leaveGroup(multicastAddress, networkInterface, source), handler);
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public DatagramServer block(InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress sourceToBlock, Handler<AsyncResult<DatagramServer>> handler) {
    configurable = false;
    addListener(channel().block(multicastAddress, networkInterface, sourceToBlock), handler);
    return  this;
  }

  @Override
  public DatagramServer block(InetAddress multicastAddress, InetAddress sourceToBlock, Handler<AsyncResult<DatagramServer>> handler) {
    configurable = false;
    addListener(channel().block(multicastAddress, sourceToBlock), handler);
    return this;
  }

  @Override
  public DatagramServer listen(String address, int port, Handler<AsyncResult<DatagramServer>> handler) {
    configurable = false;
    return listen(new InetSocketAddress(address, port), handler);
  }

  @Override
  public DatagramServer listen(int port, Handler<AsyncResult<DatagramServer>> handler) {
    configurable = false;
    return listen(new InetSocketAddress(port), handler);
  }

  @Override
  public DatagramServer listen(InetSocketAddress local, Handler<AsyncResult<DatagramServer>> handler) {
    configurable = false;
    ChannelFuture future = channel().bind(local);
    addListener(future, handler);
    return this;
  }

  @Override
  public DatagramServer dataHandler(Handler<DatagramPacket> handler) {
    dataHandler = handler;
    return this;
  }

  final void handleMessage(DatagramPacket message) {
    if (dataHandler != null) {
      dataHandler.handle(message);
    }
  }

  @Override
  public DatagramServer exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }
}
