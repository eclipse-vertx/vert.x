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

package io.vertx.core.impl.transports;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.unix.DomainSocketAddress;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.net.ClientOptionsBase;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.core.spi.transport.Transport;

import java.net.SocketAddress;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class EpollTransport implements Transport {

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

  public EpollTransport() {
  }

  @Override
  public boolean supportsDomainSockets() {
    return true;
  }

  @Override
  public SocketAddress convert(io.vertx.core.net.SocketAddress address) {
    if (address.isDomainSocket()) {
      return new DomainSocketAddress(address.path());
    } else {
      return Transport.super.convert(address);
    }
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
    return Epoll.isAvailable();
  }

  @Override
  public Throwable unavailabilityCause() {
    return Epoll.unavailabilityCause();
  }

  @Override
  public IoHandlerFactory ioHandlerFactory() {
    return EpollIoHandler.newFactory();
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
  public ChannelFactory<? extends Channel> channelFactory(boolean domainSocket) {
    if (domainSocket) {
      return EpollDomainSocketChannel::new;
    } else {
      return EpollSocketChannel::new;
    }
  }

  public ChannelFactory<? extends ServerChannel> serverChannelFactory(boolean domainSocket) {
    if (domainSocket) {
      return EpollServerDomainSocketChannel::new;
    }
    return EpollServerSocketChannel::new;
  }

  @Override
  public void configure(DatagramChannel channel, DatagramSocketOptions options) {
    channel.config().setOption(EpollChannelOption.SO_REUSEPORT, options.isReusePort());
    Transport.super.configure(channel, options);
  }

  @Override
  public void configure(NetServerOptions options, boolean domainSocket, ServerBootstrap bootstrap) {
    if (!domainSocket) {
      bootstrap.option(EpollChannelOption.SO_REUSEPORT, options.isReusePort());
      if (options.isTcpFastOpen()) {
        bootstrap.option(ChannelOption.TCP_FASTOPEN, options.isTcpFastOpen() ? pendingFastOpenRequestsThreshold : 0);
      }
      bootstrap.childOption(EpollChannelOption.TCP_QUICKACK, options.isTcpQuickAck());
      bootstrap.childOption(EpollChannelOption.TCP_CORK, options.isTcpCork());
    }
    Transport.super.configure(options, domainSocket, bootstrap);
  }

  @Override
  public void configure(ClientOptionsBase options, int connectTimeout, boolean domainSocket, Bootstrap bootstrap) {
    if (!domainSocket) {
      if (options.isTcpFastOpen()) {
        bootstrap.option(ChannelOption.TCP_FASTOPEN_CONNECT, options.isTcpFastOpen());
      }
      bootstrap.option(EpollChannelOption.TCP_USER_TIMEOUT, options.getTcpUserTimeout());
      bootstrap.option(EpollChannelOption.TCP_QUICKACK, options.isTcpQuickAck());
      bootstrap.option(EpollChannelOption.TCP_CORK, options.isTcpCork());
    }
    Transport.super.configure(options, connectTimeout, domainSocket, bootstrap);
  }
}
