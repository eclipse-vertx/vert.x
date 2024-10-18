/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.impl.transports;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.channel.uring.*;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.net.ClientOptionsBase;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.core.spi.transport.Transport;

import java.net.SocketAddress;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class IoUringTransport implements Transport {

  private static volatile int pendingFastOpenRequestsThreshold = 256;

  /**
   * Return the number of pending TFO connections in SYN-RCVD state for TCP_FASTOPEN.
   * <p>
   * {@see #setPendingFastOpenRequestsThreshold}
   */
  public static int getPendingFastOpenRequestsThreshold() {
    return pendingFastOpenRequestsThreshold;
  }

  /**
   * Set the number of pending TFO connections in SYN-RCVD state for TCP_FASTOPEN
   * <p/>
   * If this value goes over a certain limit the server disables all TFO connections.
   */
  public static void setPendingFastOpenRequestsThreshold(int value) {
    if (value < 0) {
      throw new IllegalArgumentException("Invalid " + value);
    }
    pendingFastOpenRequestsThreshold = value;
  }

  public IoUringTransport() {
  }

  @Override
  public boolean supportsDomainSockets() {
    return false;
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
    return Transport.super.convert(address);
  }

  @Override
  public io.vertx.core.net.SocketAddress convert(SocketAddress address) {
    if (address instanceof DomainSocketAddress) {
      return new SocketAddressImpl(((DomainSocketAddress) address).path());
    }
    return Transport.super.convert(address);
  }

  @Override
  public boolean isAvailable() {
    return IoUring.isAvailable();
  }

  @Override
  public Throwable unavailabilityCause() {
    return IoUring.unavailabilityCause();
  }

  @Override
  public IoHandlerFactory ioHandlerFactory() {
    return IoUringIoHandler.newFactory();
  }

  @Override
  public DatagramChannel datagramChannel() {
    return new IoUringDatagramChannel();
  }

  @Override
  public DatagramChannel datagramChannel(InternetProtocolFamily family) {
    return new IoUringDatagramChannel();
  }

  @Override
  public ChannelFactory<? extends Channel> channelFactory(boolean domainSocket) {
    if (domainSocket) {
      throw new IllegalArgumentException();
    }
    return IoUringSocketChannel::new;
  }

  @Override
  public ChannelFactory<? extends ServerChannel> serverChannelFactory(boolean domainSocket) {
    if (domainSocket) {
      throw new IllegalArgumentException();
    }
    return IoUringServerSocketChannel::new;
  }

  @Override
  public void configure(DatagramChannel channel, DatagramSocketOptions options) {
    channel.config().setOption(IoUringChannelOption.SO_REUSEPORT, options.isReusePort());
    Transport.super.configure(channel, options);
  }

  @Override
  public void configure(NetServerOptions options, boolean domainSocket, ServerBootstrap bootstrap) {
    if (domainSocket) {
      throw new IllegalArgumentException();
    }
    bootstrap.option(IoUringChannelOption.SO_REUSEPORT, options.isReusePort());
    if (options.isTcpFastOpen()) {
      bootstrap.option(IoUringChannelOption.TCP_FASTOPEN, options.isTcpFastOpen() ? pendingFastOpenRequestsThreshold : 0);
    }
    bootstrap.childOption(IoUringChannelOption.TCP_QUICKACK, options.isTcpQuickAck());
    bootstrap.childOption(IoUringChannelOption.TCP_CORK, options.isTcpCork());
    Transport.super.configure(options, false, bootstrap);
  }

  @Override
  public void configure(ClientOptionsBase options, int connectTimeout, boolean domainSocket, Bootstrap bootstrap) {
    if (domainSocket) {
      throw new IllegalArgumentException();
    }
    if (options.isTcpFastOpen()) {
      bootstrap.option(IoUringChannelOption.TCP_FASTOPEN_CONNECT, options.isTcpFastOpen());
    }
    bootstrap.option(IoUringChannelOption.TCP_QUICKACK, options.isTcpQuickAck());
    bootstrap.option(IoUringChannelOption.TCP_CORK, options.isTcpCork());
    Transport.super.configure(options, connectTimeout, false, bootstrap);
  }
}
