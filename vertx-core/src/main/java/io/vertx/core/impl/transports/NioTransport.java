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

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.nio.*;
import io.vertx.core.net.TcpConfig;
import io.vertx.core.net.TcpOption;
import io.vertx.core.spi.transport.Transport;

import jdk.net.ExtendedSocketOptions;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.Set;

public class NioTransport implements Transport {

  /**
   * The NIO transport, always there.
   */
  public static final Transport INSTANCE = new NioTransport();

  // The TCP keep-alive probe options, or null when the platform does not support them.
  //
  // ExtendedSocketOptions declares these since Java 11, but a platform need not implement them, and Netty logs a
  // warning for every channel configured with an option the socket does not support. Support is therefore probed
  // once here, per option, and an unsupported option is simply never set rather than failing the connection.
  private static final ChannelOption<Integer> NIO_TCP_KEEPIDLE;
  private static final ChannelOption<Integer> NIO_TCP_KEEPINTVL;
  private static final ChannelOption<Integer> NIO_TCP_KEEPCNT;

  static {
    Set<SocketOption<?>> supported;
    try (SocketChannel channel = SocketChannel.open()) {
      supported = channel.supportedOptions();
    } catch (IOException | RuntimeException e) {
      supported = Collections.emptySet();
    }
    NIO_TCP_KEEPIDLE = channelOption(supported, ExtendedSocketOptions.TCP_KEEPIDLE);
    NIO_TCP_KEEPINTVL = channelOption(supported, ExtendedSocketOptions.TCP_KEEPINTERVAL);
    NIO_TCP_KEEPCNT = channelOption(supported, ExtendedSocketOptions.TCP_KEEPCOUNT);
  }

  private static <T> ChannelOption<T> channelOption(Set<SocketOption<?>> supported, SocketOption<T> option) {
    return supported.contains(option) ? NioChannelOption.of(option) : null;
  }

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
  public ChannelFactory<? extends DatagramChannel> datagramChannelFactory() {
    return NioDatagramChannel::new;
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

  @Override
  public void configure(TcpConfig config, boolean domainSocket, Bootstrap bootstrap) {
    if (!domainSocket) {
      if (NIO_TCP_KEEPCNT != null && !isNullOrZero(config, TcpOption.KEEPCNT)) {
        configOption(bootstrap, config, TcpOption.KEEPCNT, NIO_TCP_KEEPCNT);
      }
      if (NIO_TCP_KEEPINTVL != null && !isNullOrZero(config, TcpOption.KEEPINTVL)) {
        configOption(bootstrap, config, TcpOption.KEEPINTVL, NIO_TCP_KEEPINTVL);
      }
      if (NIO_TCP_KEEPIDLE != null && !isNullOrZero(config, TcpOption.KEEPIDLE)) {
        configOption(bootstrap, config, TcpOption.KEEPIDLE, NIO_TCP_KEEPIDLE);
      }
    }
    Transport.super.configure(config, domainSocket, bootstrap);
  }

  @Override
  public void configure(TcpConfig config, boolean domainSocket, ServerBootstrap bootstrap) {
    if (!domainSocket) {
      if (NIO_TCP_KEEPCNT != null && !isNullOrZero(config, TcpOption.KEEPCNT)) {
        configChildOption(bootstrap, config, TcpOption.KEEPCNT, NIO_TCP_KEEPCNT);
      }
      if (NIO_TCP_KEEPINTVL != null && !isNullOrZero(config, TcpOption.KEEPINTVL)) {
        configChildOption(bootstrap, config, TcpOption.KEEPINTVL, NIO_TCP_KEEPINTVL);
      }
      if (NIO_TCP_KEEPIDLE != null && !isNullOrZero(config, TcpOption.KEEPIDLE)) {
        configChildOption(bootstrap, config, TcpOption.KEEPIDLE, NIO_TCP_KEEPIDLE);
      }
    }
    Transport.super.configure(config, domainSocket, bootstrap);
  }

  /**
   * Returns true if the given {@code option} of {@code config} is not set or set to {@code 0}.
   *
   * @param config the config
   * @param option a tcp option
   * @return true if the given option is not set or set to {@code 0}.
   * @param <T> the value type of the option, must be a {@link Number}.
   */
  public static <T extends Number> boolean isNullOrZero(TcpConfig config, TcpOption<T> option) {
    Number value;
    return (value = config.getOption(option)) == null || value.longValue() == 0;
  }

  public static <T> void configOption(AbstractBootstrap<?, ?> bootstrap, TcpConfig config, TcpOption<T> tcpOption, ChannelOption<T> channelOption) {
    T value = config.getOption(tcpOption);
    if (value != null) {
      bootstrap.option(channelOption, value);
    }
  }

  public static <T> void configChildOption(ServerBootstrap bootstrap, TcpConfig config, TcpOption<T> tcpOption, ChannelOption<T> channelOption) {
    T value = config.getOption(tcpOption);
    if (value != null) {
      bootstrap.childOption(channelOption, value);
    }
  }
}
