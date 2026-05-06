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
import io.netty.channel.unix.UnixChannelOption;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.net.TcpConfig;
import io.vertx.core.net.TcpOption;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.core.spi.transport.Transport;

import java.net.SocketAddress;

import static io.vertx.core.impl.transports.NioTransport.configChildOption;
import static io.vertx.core.impl.transports.NioTransport.configOption;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class EpollTransport implements Transport {

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
  public DatagramChannel datagramChannel(InternetProtocolFamily family) {
    return new EpollDatagramChannel();
  }

  @Override
  public ChannelFactory<? extends DatagramChannel> datagramChannelFactory() {
    return EpollDatagramChannel::new;
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
  public void configure(TcpConfig config, boolean domainSocket, ServerBootstrap bootstrap) {
    if (!domainSocket) {
      bootstrap.option(UnixChannelOption.SO_REUSEPORT, config.isSoReusePort());
      configOption(bootstrap, config, TcpOption.FASTOPEN, EpollChannelOption.TCP_FASTOPEN);
      configChildOption(bootstrap, config, TcpOption.USER_TIMEOUT, EpollChannelOption.TCP_USER_TIMEOUT);
      configChildOption(bootstrap, config, TcpOption.QUICKACK, EpollChannelOption.TCP_QUICKACK);
      configChildOption(bootstrap, config, TcpOption.CORK, EpollChannelOption.TCP_CORK);
      configChildOption(bootstrap, config, TcpOption.KEEPCNT, EpollChannelOption.TCP_KEEPCNT);
      configChildOption(bootstrap, config, TcpOption.KEEPINTVL, EpollChannelOption.TCP_KEEPINTVL);
      configChildOption(bootstrap, config, TcpOption.KEEPIDLE, EpollChannelOption.TCP_KEEPIDLE);
    }
    Transport.super.configure(config, domainSocket, bootstrap);
  }

  @Override
  public void configure(TcpConfig config, boolean domainSocket, Bootstrap bootstrap) {
    if (!domainSocket) {
      configOption(bootstrap, config, TcpOption.FASTOPEN_CONNECT, EpollChannelOption.TCP_FASTOPEN_CONNECT);
      configOption(bootstrap, config, TcpOption.USER_TIMEOUT, EpollChannelOption.TCP_USER_TIMEOUT);
      configOption(bootstrap, config, TcpOption.QUICKACK, EpollChannelOption.TCP_QUICKACK);
      configOption(bootstrap, config, TcpOption.CORK, EpollChannelOption.TCP_CORK);
      configOption(bootstrap, config, TcpOption.KEEPCNT, EpollChannelOption.TCP_KEEPCNT);
      configOption(bootstrap, config, TcpOption.KEEPINTVL, EpollChannelOption.TCP_KEEPINTVL);
      configOption(bootstrap, config, TcpOption.KEEPIDLE, EpollChannelOption.TCP_KEEPIDLE);
    }
    Transport.super.configure(config, domainSocket, bootstrap);
  }
}
