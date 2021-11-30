/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl.transport;

import java.net.SocketAddress;
import java.util.concurrent.ThreadFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.incubator.channel.uring.IOUring;
import io.netty.incubator.channel.uring.IOUringChannelOption;
import io.netty.incubator.channel.uring.IOUringDatagramChannel;
import io.netty.incubator.channel.uring.IOUringEventLoopGroup;
import io.netty.incubator.channel.uring.IOUringServerSocketChannel;
import io.netty.incubator.channel.uring.IOUringSocketChannel;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.net.ClientOptionsBase;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.impl.SocketAddressImpl;

public class IOUringTransport extends Transport {

  private static volatile int pendingFastOpenRequestsThreshold = 256;

  /**
   * Return the number of of pending TFO connections in SYN-RCVD state for TCP_FASTOPEN.
   * <p>
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

  IOUringTransport() {
    super(false);
  }

  @Override
  public boolean supportFileRegion() {
    return false;
  }

  @Override
  public SocketAddress convert(io.vertx.core.net.SocketAddress address) {
    if (address.isDomainSocket()) {
      throw new IllegalArgumentException("Domain socket not supported by IOUring transport");
    }
    return super.convert(address);
  }

  @Override
  public io.vertx.core.net.SocketAddress convert(SocketAddress address) {
    if (address instanceof DomainSocketAddress) {
      return new SocketAddressImpl(((DomainSocketAddress) address).path());
    }
    return super.convert(address);
  }

  @Override
  public boolean isAvailable() {
    return IOUring.isAvailable();
  }

  @Override
  public Throwable unavailabilityCause() {
    return IOUring.unavailabilityCause();
  }

  @Override
  public EventLoopGroup eventLoopGroup(int type, int nThreads, ThreadFactory threadFactory, int ignoredIoRatio) {
    IOUringEventLoopGroup eventLoopGroup = new IOUringEventLoopGroup(nThreads, threadFactory);
    return eventLoopGroup;
  }

  @Override
  public DatagramChannel datagramChannel() {
    return new IOUringDatagramChannel();
  }

  @Override
  public DatagramChannel datagramChannel(InternetProtocolFamily family) {
    return new IOUringDatagramChannel();
  }

  @Override
  public ChannelFactory<? extends Channel> channelFactory(boolean domainSocket) {
    if (domainSocket) {
      throw new IllegalArgumentException();
    }
    return IOUringSocketChannel::new;
  }

  @Override
  public ChannelFactory<? extends ServerChannel> serverChannelFactory(boolean domainSocket) {
    if (domainSocket) {
      throw new IllegalArgumentException();
    }
    return IOUringServerSocketChannel::new;
  }

  @Override
  public void configure(DatagramChannel channel, DatagramSocketOptions options) {
    channel.config().setOption(IOUringChannelOption.SO_REUSEPORT, options.isReusePort());
    super.configure(channel, options);
  }

  @Override
  public void configure(NetServerOptions options, boolean domainSocket, ServerBootstrap bootstrap) {
    if (domainSocket) {
      throw new IllegalArgumentException();
    }
    bootstrap.option(IOUringChannelOption.SO_REUSEPORT, options.isReusePort());
    if (options.isTcpFastOpen()) {
      bootstrap.option(IOUringChannelOption.TCP_FASTOPEN, options.isTcpFastOpen() ? pendingFastOpenRequestsThreshold : 0);
    }
    bootstrap.childOption(IOUringChannelOption.TCP_QUICKACK, options.isTcpQuickAck());
    bootstrap.childOption(IOUringChannelOption.TCP_CORK, options.isTcpCork());
    super.configure(options, false, bootstrap);
  }

  @Override
  public void configure(ClientOptionsBase options, boolean domainSocket, Bootstrap bootstrap) {
    if (domainSocket) {
      throw new IllegalArgumentException();
    }
    if (options.isTcpFastOpen()) {
      bootstrap.option(IOUringChannelOption.TCP_FASTOPEN_CONNECT, options.isTcpFastOpen());
    }
    bootstrap.option(IOUringChannelOption.TCP_QUICKACK, options.isTcpQuickAck());
    bootstrap.option(IOUringChannelOption.TCP_CORK, options.isTcpCork());
    super.configure(options, false, bootstrap);
  }
}
