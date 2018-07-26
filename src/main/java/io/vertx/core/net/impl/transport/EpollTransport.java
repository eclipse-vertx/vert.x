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

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
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
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.net.ClientOptionsBase;
import io.vertx.core.net.NetServerOptions;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ThreadFactory;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class EpollTransport extends Transport {

  private static volatile int pendingFastOpenRequestsThreshold = 256;

  /**
   * Return the number of of pending TFO connections in SYN-RCVD state for TCP_FASTOPEN.
   *
   * {@see #setPendingFastOpenRequestsThreshold}
   */
  public static int getPendingFastOpenRequestsThreshold() {
    return pendingFastOpenRequestsThreshold;
  }

  /**
   * Set the number of of pending TFO connections in SYN-RCVD state for TCP_FASTOPEN
   * <p/>
   * If this value goes over a certain limit the server disables all TFO connections.
   */
  public static void setPendingFastOpenRequestsThreshold(int value) {
    if (value < 0) {
      throw new IllegalArgumentException("Invalid " + value);
    }
    pendingFastOpenRequestsThreshold = value;
  }

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
  public ChannelFactory<? extends Channel> channelFactory(boolean domain) {
    if (domain) {
      return EpollDomainSocketChannel::new;
    } else {
      return EpollSocketChannel::new;
    }
  }

  public ChannelFactory<? extends ServerChannel> serverChannelFactory(boolean domain) {
    if (domain) {
      return EpollServerDomainSocketChannel::new;
    }
    return EpollServerSocketChannel::new;
  }

  @Override
  public void configure(DatagramChannel channel, DatagramSocketOptions options) {
    channel.config().setOption(EpollChannelOption.SO_REUSEPORT, options.isReusePort());
    super.configure(channel, options);
  }

  @Override
  public void configure(NetServerOptions options, ServerBootstrap bootstrap) {
    bootstrap.option(EpollChannelOption.SO_REUSEPORT, options.isReusePort());
    if (options.isTcpFastOpen()) {
      bootstrap.option(EpollChannelOption.TCP_FASTOPEN, options.isTcpFastOpen() ? pendingFastOpenRequestsThreshold : 0);
    }
    bootstrap.childOption(EpollChannelOption.TCP_QUICKACK, options.isTcpQuickAck());
    bootstrap.childOption(EpollChannelOption.TCP_CORK, options.isTcpCork());
    super.configure(options, bootstrap);
  }

  @Override
  public void configure(ClientOptionsBase options, Bootstrap bootstrap) {
    if (options.isTcpFastOpen()) {
      bootstrap.option(EpollChannelOption.TCP_FASTOPEN_CONNECT, options.isTcpFastOpen());
    }
    bootstrap.option(EpollChannelOption.TCP_QUICKACK, options.isTcpQuickAck());
    bootstrap.option(EpollChannelOption.TCP_CORK, options.isTcpCork());
    super.configure(options, bootstrap);
  }
}
