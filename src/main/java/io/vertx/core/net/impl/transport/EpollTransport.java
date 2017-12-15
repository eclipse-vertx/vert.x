/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net.impl.transport;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.unix.DomainSocketAddress;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ThreadFactory;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class EpollTransport extends Transport {

  EpollTransport() {
  }

  @Override
  public SocketAddress convert(io.vertx.core.net.SocketAddress address, boolean resolved) {
    if (address.path() != null) {
      return new DomainSocketAddress(address.path());
    } else {
      if (resolved) {
        return new InetSocketAddress(address.host(), address.port());
      } else {
        return InetSocketAddress.createUnresolved(address.host(), address.port());
      }
    }
  }

  @Override
  public boolean isAvailable() {
    return Epoll.isAvailable();
  }

  @Override
  public Throwable unavailabilityCause() {
    return Epoll.unavailabilityCause();
  }

  @Override
  public EventLoopGroup eventLoopGroup(int nThreads, ThreadFactory threadFactory, int ioRatio) {
    EpollEventLoopGroup eventLoopGroup = new EpollEventLoopGroup(nThreads, threadFactory);
    eventLoopGroup.setIoRatio(ioRatio);
    return eventLoopGroup;
  }

  @Override
  public DatagramChannel datagramChannel() {
    return new EpollDatagramChannel();
  }

  @Override
  public DatagramChannel datagramChannel(InternetProtocolFamily family) {
    return new EpollDatagramChannel();
  }

  @Override
  public Class<? extends Channel> channelType(boolean domain) {
    if (domain) {
      return EpollDomainSocketChannel.class;
    } else {
      return EpollSocketChannel.class;
    }
  }

  public Class<? extends ServerChannel> serverChannelType(boolean domain) {
    if (domain) {
      return EpollServerDomainSocketChannel.class;
    }
    return EpollServerSocketChannel.class;
  }

  @Override
  public ChannelOption<?> channelOption(String name) {
    switch (name) {
      case "SO_REUSEPORT":
        return EpollChannelOption.SO_REUSEPORT;
      case "TCP_QUICKACK":
        return EpollChannelOption.TCP_QUICKACK;
      case "TCP_CORK":
        return EpollChannelOption.TCP_CORK;
      case "TCP_FASTOPEN":
        return EpollChannelOption.TCP_FASTOPEN;
      default:
        return null;
    }
  }
}
