/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.impl.transports;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.ServerChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.nio.*;
import io.vertx.core.spi.transport.Transport;

import java.net.SocketAddress;

public class NioTransport implements Transport {
  /**
   * The NIO transport, always there.
   */
  public static final Transport INSTANCE = new NioTransport();

  private final UnixDomainSocketNioTransport unixDomainSocketNioTransport = UnixDomainSocketNioTransport.load();

  @Override
  public boolean supportsDomainSockets() {
    return unixDomainSocketNioTransport != null;
  }

  @Override
  public SocketAddress convert(io.vertx.core.net.SocketAddress address) {
    if (address.isDomainSocket() && unixDomainSocketNioTransport != null) {
      return unixDomainSocketNioTransport.convert(address);
    } else {
      return Transport.super.convert(address);
    }
  }

  @Override
  public io.vertx.core.net.SocketAddress convert(SocketAddress address) {
    if (unixDomainSocketNioTransport != null && unixDomainSocketNioTransport.isUnixDomainSocketAddress(address)) {
      return unixDomainSocketNioTransport.convert(address);
    }
    return Transport.super.convert(address);
  }

  @Override
  public IoHandlerFactory ioHandlerFactory() {
    return NioIoHandler.newFactory();
  }

  @Override
  public DatagramChannel datagramChannel() {
    return new NioDatagramChannel();
  }

  @Override
  public DatagramChannel datagramChannel(InternetProtocolFamily family) {
    switch (family) {
      case IPv4:
        return new NioDatagramChannel(InternetProtocolFamily.IPv4);
      case IPv6:
        return new NioDatagramChannel(InternetProtocolFamily.IPv6);
      default:
        throw new UnsupportedOperationException();
    }
  }

  @Override
  public ChannelFactory<? extends Channel> channelFactory(boolean domainSocket) {
    if (domainSocket) {
      if (unixDomainSocketNioTransport == null) {
        throw new IllegalArgumentException("Domain sockets require JDK 16 and above, or the usage of a native transport");
      }
      return NioDomainSocketChannel::new;
    } else {
      return NioSocketChannel::new;
    }
  }

  @Override
  public ChannelFactory<? extends ServerChannel> serverChannelFactory(boolean domainSocket) {
    if (domainSocket) {
      if (unixDomainSocketNioTransport == null) {
        throw new IllegalArgumentException("Domain sockets require JDK 16 and above, or the usage of a native transport");
      }
      return NioServerDomainSocketChannel::new;
    }
    return NioServerSocketChannel::new;
  }
}
