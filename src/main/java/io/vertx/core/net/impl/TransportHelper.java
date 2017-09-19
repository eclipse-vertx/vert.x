/*
 * Copyright (c) 2011-2017 The original author or authors
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
package io.vertx.core.net.impl;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.socket.DatagramChannel;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.net.ClientOptionsBase;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.spi.transport.Transport;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.function.BiConsumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TransportHelper {

  private final Transport transport;

  public TransportHelper(Transport transport) {
    this.transport = transport;
  }

  private void setOption(String name, Object value, BiConsumer<ChannelOption<Object>, Object> consumer) {
    ChannelOption<Object> option = (ChannelOption<Object>) transport.channelOption(name);
    if (option != null) {
      consumer.accept(option, value);
    }
  }

  public void configure(DatagramChannel channel, DatagramSocketOptions options) {
    channel.config().setAllocator(PartialPooledByteBufAllocator.INSTANCE);
    if (options.getSendBufferSize() != -1) {
      channel.config().setSendBufferSize(options.getSendBufferSize());
    }
    if (options.getReceiveBufferSize() != -1) {
      channel.config().setReceiveBufferSize(options.getReceiveBufferSize());
      channel.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(options.getReceiveBufferSize()));
    }
    setOption("SO_REUSEPORT", options.isReusePort(), channel.config()::setOption);
    channel.config().setOption(ChannelOption.SO_REUSEADDR, options.isReuseAddress());
    if (options.getTrafficClass() != -1) {
      channel.config().setTrafficClass(options.getTrafficClass());
    }
    channel.config().setBroadcast(options.isBroadcast());
    if (transport == Transport.JDK) {
      channel.config().setLoopbackModeDisabled(options.isLoopbackModeDisabled());
      if (options.getMulticastTimeToLive() != -1) {
        channel.config().setTimeToLive(options.getMulticastTimeToLive());
      }
      if (options.getMulticastNetworkInterface() != null) {
        try {
          channel.config().setNetworkInterface(NetworkInterface.getByName(options.getMulticastNetworkInterface()));
        } catch (SocketException e) {
          throw new IllegalArgumentException("Could not find network interface with name " + options.getMulticastNetworkInterface());
        }
      }
    }
  }

  public void configure(ClientOptionsBase options, Bootstrap bootstrap) {
    BiConsumer<ChannelOption<Object>, Object> setter = bootstrap::option;
    setOption("TCK_CORK", options.isTcpCork(), setter);
    setOption("TCK_QUICKACK", options.isTcpQuickAck(), setter);
    setOption("TCK_FASTOPEN", options.isTcpFastOpen(), setter);
    setOption("SO_REUSEPORT", options.isReusePort(), setter);
    if (options.getLocalAddress() != null) {
      bootstrap.localAddress(options.getLocalAddress(), 0);
    }
    bootstrap.option(ChannelOption.TCP_NODELAY, options.isTcpNoDelay());
    if (options.getSendBufferSize() != -1) {
      bootstrap.option(ChannelOption.SO_SNDBUF, options.getSendBufferSize());
    }
    if (options.getReceiveBufferSize() != -1) {
      bootstrap.option(ChannelOption.SO_RCVBUF, options.getReceiveBufferSize());
      bootstrap.option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(options.getReceiveBufferSize()));
    }
    if (options.getSoLinger() != -1) {
      bootstrap.option(ChannelOption.SO_LINGER, options.getSoLinger());
    }
    if (options.getTrafficClass() != -1) {
      bootstrap.option(ChannelOption.IP_TOS, options.getTrafficClass());
    }
    bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, options.getConnectTimeout());
    bootstrap.option(ChannelOption.ALLOCATOR, PartialPooledByteBufAllocator.INSTANCE);
    bootstrap.option(ChannelOption.SO_KEEPALIVE, options.isTcpKeepAlive());
    bootstrap.option(ChannelOption.SO_REUSEADDR, options.isReuseAddress());
  }

  public void configure(NetServerOptions options, ServerBootstrap bootstrap) {
    BiConsumer<ChannelOption<Object>, Object> setter = bootstrap::childOption;
    setOption("TCK_CORK", options.isTcpCork(), setter);
    setOption("TCK_QUICKACK", options.isTcpQuickAck(), setter);
    setOption("TCK_FASTOPEN", options.isTcpFastOpen(), setter);
    setOption("SO_REUSEPORT", options.isReusePort(), setter);
    bootstrap.childOption(ChannelOption.TCP_NODELAY, options.isTcpNoDelay());
    if (options.getSendBufferSize() != -1) {
      bootstrap.childOption(ChannelOption.SO_SNDBUF, options.getSendBufferSize());
    }
    if (options.getReceiveBufferSize() != -1) {
      bootstrap.childOption(ChannelOption.SO_RCVBUF, options.getReceiveBufferSize());
      bootstrap.childOption(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(options.getReceiveBufferSize()));
    }
    if (options.getSoLinger() != -1) {
      bootstrap.option(ChannelOption.SO_LINGER, options.getSoLinger());
    }
    if (options.getTrafficClass() != -1) {
      bootstrap.childOption(ChannelOption.IP_TOS, options.getTrafficClass());
    }
    bootstrap.childOption(ChannelOption.ALLOCATOR, PartialPooledByteBufAllocator.INSTANCE);
    bootstrap.childOption(ChannelOption.SO_KEEPALIVE, options.isTcpKeepAlive());
    bootstrap.option(ChannelOption.SO_REUSEADDR, options.isReuseAddress());
    if (options.getAcceptBacklog() != -1) {
      bootstrap.option(ChannelOption.SO_BACKLOG, options.getAcceptBacklog());
    }
  }
}
