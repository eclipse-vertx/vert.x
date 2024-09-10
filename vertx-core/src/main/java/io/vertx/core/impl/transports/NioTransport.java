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

import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.vertx.core.spi.transport.Transport;

public class NioTransport implements Transport {
  /**
   * The NIO transport, always there.
   */
  public static final Transport INSTANCE = new NioTransport();

  @Override
  public IoHandlerFactory ioHandlerFactory() {
    return NioIoHandler.newFactory();
  }

  public DatagramChannel datagramChannel() {
    return new NioDatagramChannel();
  }

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

  public ChannelFactory<? extends Channel> channelFactory(boolean domainSocket) {
    if (domainSocket) {
      throw new IllegalArgumentException("The Vertx instance must be created with the preferNativeTransport option set to true to create domain sockets");
    }
    return NioSocketChannel::new;
  }

  public ChannelFactory<? extends ServerChannel> serverChannelFactory(boolean domainSocket) {
    if (domainSocket) {
      throw new IllegalArgumentException("The Vertx instance must be created with the preferNativeTransport option set to true to create domain sockets");
    }
    return NioServerSocketChannel::new;
  }
}
