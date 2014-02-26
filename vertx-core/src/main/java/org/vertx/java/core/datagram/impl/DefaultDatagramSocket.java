/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package org.vertx.java.core.datagram.impl;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.datagram.DatagramSocket;
import org.vertx.java.core.impl.DefaultFutureResult;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.net.impl.ConnectionBase;

import java.net.*;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class DefaultDatagramSocket extends ConnectionBase
        implements DatagramSocket {

  private Handler<Void> drainHandler;
  protected boolean configurable = true;
  private Handler<org.vertx.java.core.datagram.DatagramPacket> dataHandler;
  public DefaultDatagramSocket(VertxInternal vertx, org.vertx.java.core.datagram.InternetProtocolFamily family) {
    super(vertx, createChannel(family), vertx.getOrCreateContext());
    channel().config().setOption(ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION, true);
    context.getEventLoop().register(channel);
    channel.pipeline().addLast("handler", new DatagramServerHandler(this.vertx, this));
    channel().config().setMaxMessagesPerRead(1);
  }

  @Override
  public DatagramSocket listenMulticastGroup(String multicastAddress, Handler<AsyncResult<DatagramSocket>> handler) {
    configurable = false;
    try {
      addListener(channel().joinGroup(InetAddress.getByName(multicastAddress)), handler);
    } catch (UnknownHostException e) {
      notifyException(handler, e);
    }
    return this;
  }

  @Override
  public DatagramSocket listenMulticastGroup(String multicastAddress, String networkInterface, String source, Handler<AsyncResult<DatagramSocket>> handler) {
    configurable = false;
    try {
      InetAddress sourceAddress;
      if (source == null) {
        sourceAddress = null;
      } else {
        sourceAddress = InetAddress.getByName(source);
      }
      addListener(channel().joinGroup(InetAddress.getByName(multicastAddress),
              NetworkInterface.getByName(networkInterface), sourceAddress), handler);
    } catch (Exception e) {
      notifyException(handler, e);
    }
    return this;
  }

  @Override
  public DatagramSocket unlistenMulticastGroup(String multicastAddress, Handler<AsyncResult<DatagramSocket>> handler) {
    configurable = false;
    try {
      addListener(channel().leaveGroup(InetAddress.getByName(multicastAddress)), handler);
    } catch (UnknownHostException e) {
      notifyException(handler, e);
    }
    return this;
  }

  @Override
  public DatagramSocket unlistenMulticastGroup(String multicastAddress, String networkInterface, String source, Handler<AsyncResult<DatagramSocket>> handler) {
    configurable = false;
    try {
      InetAddress sourceAddress;
      if (source == null) {
        sourceAddress = null;
      } else {
        sourceAddress = InetAddress.getByName(source);
      }
      addListener(channel().leaveGroup(InetAddress.getByName(multicastAddress),
              NetworkInterface.getByName(networkInterface), sourceAddress), handler);
    } catch (Exception e) {
      notifyException(handler, e);
    }
    return this;
  }

  @Override
  public DatagramSocket blockMulticastGroup(String multicastAddress, String networkInterface, String sourceToBlock, Handler<AsyncResult<DatagramSocket>> handler) {
    configurable = false;
    try {
      InetAddress sourceAddress;
      if (sourceToBlock == null) {
        sourceAddress = null;
      } else {
        sourceAddress = InetAddress.getByName(sourceToBlock);
      }
      addListener(channel().block(InetAddress.getByName(multicastAddress),
              NetworkInterface.getByName(networkInterface), sourceAddress), handler);
    } catch (Exception e) {
      notifyException(handler, e);
    }
    return  this;
  }

  @Override
  public DatagramSocket blockMulticastGroup(String multicastAddress, String sourceToBlock, Handler<AsyncResult<DatagramSocket>> handler) {
    configurable = false;
    try {
      addListener(channel().block(InetAddress.getByName(multicastAddress), InetAddress.getByName(sourceToBlock)), handler);
    } catch (UnknownHostException e) {
      notifyException(handler, e);
    }
    return this;
  }

  @Override
  public DatagramSocket listen(String address, int port, Handler<AsyncResult<DatagramSocket>> handler) {
    configurable = false;
    return listen(new InetSocketAddress(address, port), handler);
  }

  @Override
  public DatagramSocket listen(int port, Handler<AsyncResult<DatagramSocket>> handler) {
    configurable = false;
    return listen(new InetSocketAddress(port), handler);
  }

  @Override
  public DatagramSocket listen(InetSocketAddress local, Handler<AsyncResult<DatagramSocket>> handler) {
    configurable = false;
    ChannelFuture future = channel().bind(local);
    addListener(future, handler);
    return this;
  }

  @Override
  public DatagramSocket dataHandler(Handler<org.vertx.java.core.datagram.DatagramPacket> handler) {
    dataHandler = handler;
    return this;
  }

  final void handleMessage(org.vertx.java.core.datagram.DatagramPacket message) {
    if (dataHandler != null) {
      dataHandler.handle(message);
    }
  }

  @Override
  public DatagramSocket exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }

  private void checkConfigurable() {
    if (!configurable) {
      throw new IllegalStateException("Can't set property after connect or bind has been called");
    }
  }

  @SuppressWarnings("unchecked")
  final void addListener(ChannelFuture future, Handler<AsyncResult<DatagramSocket>> handler) {
    if (handler != null) {
      future.addListener(new DatagramChannelFutureListener<>(this, handler, vertx, context));
    }
  }

  @SuppressWarnings("unchecked")
  public DatagramSocket pause() {
    doPause();
    return this;
  }

  @SuppressWarnings("unchecked")
  public DatagramSocket resume() {
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
  @SuppressWarnings("unchecked")
  public DatagramSocket setWriteQueueMaxSize(int maxSize) {
    doSetWriteQueueMaxSize(maxSize);
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    return doWriteQueueFull();
  }

  @Override
  @SuppressWarnings("unchecked")
  public DatagramSocket drainHandler(Handler<Void> handler) {
    drainHandler = handler;
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public DatagramSocket send(Buffer packet, String host, int port, Handler<AsyncResult<DatagramSocket>> handler) {
    configurable = false;
    ChannelFuture future = channel().writeAndFlush(new DatagramPacket(packet.getByteBuf(), new InetSocketAddress(host, port)));
    addListener(future, handler);
    return this;
  }

  @Override
  public DatagramSocket send(String str, String host, int port, Handler<AsyncResult<DatagramSocket>> handler) {
    return send(new Buffer(str), host, port, handler);
  }

  @Override
  public DatagramSocket send(String str, String enc, String host, int port, Handler<AsyncResult<DatagramSocket>> handler) {
    return send(new Buffer(str, enc), host, port, handler);
  }


  @Override
  public int getSendBufferSize() {
    return channel().config().getSendBufferSize();
  }

  @Override
  @SuppressWarnings("unchecked")
  public DatagramSocket setSendBufferSize(int sendBufferSize) {
    checkConfigurable();

    channel().config().setSendBufferSize(sendBufferSize);
    return this;
  }

  @Override
  public int getReceiveBufferSize() {
    return channel().config().getReceiveBufferSize();
  }

  @Override
  @SuppressWarnings("unchecked")
  public DatagramSocket setReceiveBufferSize(int receiveBufferSize) {
    checkConfigurable();

    channel().config().setReceiveBufferSize(receiveBufferSize);
    return this;
  }

  @Override
  public int getTrafficClass() {
    return channel().config().getTrafficClass();
  }

  @Override
  @SuppressWarnings("unchecked")
  public DatagramSocket setTrafficClass(int trafficClass) {
    checkConfigurable();

    channel().config().setTrafficClass(trafficClass);
    return this;
  }

  @Override
  public boolean isReuseAddress() {
    return channel().config().isReuseAddress();
  }

  @Override
  @SuppressWarnings("unchecked")
  public DatagramSocket setReuseAddress(boolean reuseAddress) {
    checkConfigurable();

    channel().config().setReuseAddress(reuseAddress);
    return this;
  }

  @Override
  public boolean isBroadcast() {
    return channel().config().isBroadcast();
  }

  @Override
  @SuppressWarnings("unchecked")
  public DatagramSocket setBroadcast(boolean broadcast) {
    checkConfigurable();

    channel().config().setBroadcast(broadcast);
    return this;
  }

  @Override
  public boolean isMulticastLoopbackMode() {
    return channel().config().isLoopbackModeDisabled();
  }

  @Override
  @SuppressWarnings("unchecked")
  public DatagramSocket setMulticastLoopbackMode(boolean loopbackModeDisabled) {
    checkConfigurable();

    channel().config().setLoopbackModeDisabled(loopbackModeDisabled);
    return this;
  }

  @Override
  public int getMulticastTimeToLive() {
    return channel().config().getTimeToLive();
  }

  @Override
  @SuppressWarnings("unchecked")
  public DatagramSocket setMulticastTimeToLive(int ttl) {
    checkConfigurable();

    channel().config().setTimeToLive(ttl);
    return this;
  }

  @Override
  public String getMulticastNetworkInterface() {
    NetworkInterface iface =  channel().config().getNetworkInterface();
    if (iface == null) {
      return null;
    }
    return iface.getName();
  }

  @Override
  @SuppressWarnings("unchecked")
  public DatagramSocket setMulticastNetworkInterface(String iface) {
    checkConfigurable();

    try {
      channel().config().setNetworkInterface(NetworkInterface.getByName(iface));
    } catch (SocketException e) {
      throw new IllegalArgumentException("Could not find network interface with name " + iface, e);
    }
    return this;
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

  private static NioDatagramChannel createChannel(org.vertx.java.core.datagram.InternetProtocolFamily family) {
    if (family == null) {
      return new NioDatagramChannel();
    }
    switch (family) {
      case IPv4:
        return new NioDatagramChannel(InternetProtocolFamily.IPv4);
      case IPv6:
        return new NioDatagramChannel(InternetProtocolFamily.IPv6);
      default:
        return new NioDatagramChannel();
    }
  }


  private void notifyException(final Handler<AsyncResult<DatagramSocket>> handler, final Throwable cause) {
    if (context.isOnCorrectWorker(channel().eventLoop())) {
      try {
        vertx.setContext(context);
        handler.handle(new DefaultFutureResult<DatagramSocket>(cause));
      } catch (Throwable t) {
        context.reportException(t);
      }
    } else {
      context.execute(new Runnable() {
        public void run() {
          handler.handle(new DefaultFutureResult<DatagramSocket>(cause));
        }
      });
    }
  }
}
