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
import io.netty.channel.socket.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.datagram.*;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.net.impl.ConnectionBase;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.StandardProtocolFamily;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
abstract class AbstractDatagramSupport<T extends DatagramSupport> extends ConnectionBase
        implements DatagramSupport<T> {

  private Handler<Void> drainHandler;
  protected boolean configurable = true;

  AbstractDatagramSupport(VertxInternal vertx, StandardProtocolFamily family) {
    super(vertx, createChannel(family), vertx.getOrCreateContext());
    context.getEventLoop().register(channel);
  }

  private void checkConfigurable() {
    if (!configurable) {
      throw new IllegalStateException("Can't set property after connect or bind has been called");
    }
  }

  @SuppressWarnings("unchecked")
  final void addListener(ChannelFuture future, Handler<AsyncResult<T>> handler) {
    if (handler != null) {
      future.addListener(new DatagramChannelFutureListener<>((T) this, handler, vertx, context));
    }
  }

  @SuppressWarnings("unchecked")
  public T pause() {
    doPause();
    return (T) this;
  }

  @SuppressWarnings("unchecked")
  public T resume() {
    doResume();
    return (T) this;
  }

  @Override
  protected void handleInterestedOpsChanged() {
    if (drainHandler != null) {
      drainHandler.handle(null);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public T setWriteQueueMaxSize(int maxSize) {
    doSetWriteQueueMaxSize(maxSize);
    return (T) this;
  }

  @Override
  public boolean writeQueueFull() {
    return doWriteQueueFull();
  }

  @Override
  @SuppressWarnings("unchecked")
  public T drainHandler(Handler<Void> handler) {
    drainHandler = handler;
    return (T) this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public T send(Buffer packet, String host, int port, Handler<AsyncResult<T>> handler) {
    configurable = false;
    ChannelFuture future = channel().writeAndFlush(new DatagramPacket(packet.getByteBuf(), new InetSocketAddress(host, port)));
    addListener(future, handler);
    return (T) this;
  }

  @Override
  public T send(String str, String host, int port, Handler<AsyncResult<T>> handler) {
    return send(new Buffer(str), host, port, handler);
  }

  @Override
  public T send(String str, String enc, String host, int port, Handler<AsyncResult<T>> handler) {
    return send(new Buffer(str, enc), host, port, handler);
  }


  @Override
  public int getSendBufferSize() {
    return channel().config().getSendBufferSize();
  }

  @Override
  @SuppressWarnings("unchecked")
  public T setSendBufferSize(int sendBufferSize) {
    checkConfigurable();

    channel().config().setSendBufferSize(sendBufferSize);
    return (T) this;
  }

  @Override
  public int getReceiveBufferSize() {
    return channel().config().getReceiveBufferSize();
  }

  @Override
  @SuppressWarnings("unchecked")
  public T setReceiveBufferSize(int receiveBufferSize) {
    checkConfigurable();

    channel().config().setReceiveBufferSize(receiveBufferSize);
    return (T) this;
  }

  @Override
  public int getTrafficClass() {
    return channel().config().getTrafficClass();
  }

  @Override
  @SuppressWarnings("unchecked")
  public T setTrafficClass(int trafficClass) {
    checkConfigurable();

    channel().config().setTrafficClass(trafficClass);
    return (T) this;
  }

  @Override
  public boolean isReuseAddress() {
    return channel().config().isReuseAddress();
  }

  @Override
  @SuppressWarnings("unchecked")
  public T setReuseAddress(boolean reuseAddress) {
    checkConfigurable();

    channel().config().setReuseAddress(reuseAddress);
    return (T) this;
  }

  @Override
  public boolean isBroadcast() {
    return channel().config().isBroadcast();
  }

  @Override
  @SuppressWarnings("unchecked")
  public T setBroadcast(boolean broadcast) {
    checkConfigurable();

    channel().config().setBroadcast(broadcast);
    return (T) this;
  }

  @Override
  public boolean isLoopbackModeDisabled() {
    return channel().config().isLoopbackModeDisabled();
  }

  @Override
  @SuppressWarnings("unchecked")
  public T setLoopbackModeDisabled(boolean loopbackModeDisabled) {
    checkConfigurable();

    channel().config().setLoopbackModeDisabled(loopbackModeDisabled);
    return (T) this;
  }

  @Override
  public int getTimeToLive() {
    return channel().config().getTimeToLive();
  }

  @Override
  @SuppressWarnings("unchecked")
  public T setTimeToLive(int ttl) {
    checkConfigurable();

    channel().config().setTimeToLive(ttl);
    return (T) this;
  }

  @Override
  public InetAddress getInterface() {
    return channel().config().getInterface();
  }

  @Override
  @SuppressWarnings("unchecked")
  public T setInterface(InetAddress interfaceAddress) {
    checkConfigurable();

    channel().config().setInterface(interfaceAddress);
    return (T) this;
  }

  @Override
  public NetworkInterface getNetworkInterface() {
    return channel().config().getNetworkInterface();
  }

  @Override
  @SuppressWarnings("unchecked")
  public T setNetworkInterface(NetworkInterface iface) {
    checkConfigurable();

    channel().config().setNetworkInterface(iface);
    return (T) this;
  }


  @Override
  public void close(final Handler<AsyncResult<Void>> handler) {
    // make sure everything is flushed out on close
    endReadAndFlush();
    ChannelFuture future = channel.close();
    if (handler != null) {
      future.addListener(new DatagramChannelFutureListener<>(null, handler, vertx, context));
    }
  }

  protected DatagramChannel channel() {
    return (DatagramChannel) channel;
  }

  private static NioDatagramChannel createChannel(StandardProtocolFamily family) {
    if (family == null) {
      return new NioDatagramChannel();
    }
    switch (family) {
      case INET:
        return new NioDatagramChannel(InternetProtocolFamily.IPv4);
      case INET6:
        return new NioDatagramChannel(InternetProtocolFamily.IPv6);
      default:
        return new NioDatagramChannel();
    }
  }
}
