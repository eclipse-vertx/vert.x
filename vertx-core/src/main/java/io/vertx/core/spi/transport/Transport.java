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

package io.vertx.core.spi.transport;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.impl.transports.NioTransport;
import io.vertx.core.net.TcpOptions;
import io.vertx.core.net.impl.SocketAddressImpl;

import java.net.*;
import java.util.concurrent.ThreadFactory;

/**
 * The transport used by a {@link io.vertx.core.Vertx} instance.
 * <p/>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface Transport {

  int ACCEPTOR_EVENT_LOOP_GROUP = 0;
  int IO_EVENT_LOOP_GROUP = 1;

  default boolean supportsDomainSockets() {
    return false;
  }

  default boolean supportFileRegion() {
    return true;
  }

  /**
   * @return true when the transport is available.
   */
  default boolean isAvailable() {
    return true;
  }

  /**
   * @return the error that cause the unavailability when {@link #isAvailable()} returns {@code null}.
   */
  default Throwable unavailabilityCause() {
    return null;
  }

  default SocketAddress convert(io.vertx.core.net.SocketAddress address) {
    if (address.isDomainSocket()) {
      throw new IllegalArgumentException("Domain sockets require JDK 16 and above, or the usage of a native transport");
    } else {
      InetAddress ip = ((SocketAddressImpl) address).ipAddress();
      if (ip != null) {
        return new InetSocketAddress(ip, address.port());
      } else {
        return InetSocketAddress.createUnresolved(address.host(), address.port());
      }
    }
  }

  default io.vertx.core.net.SocketAddress convert(SocketAddress address) {
    if (address instanceof InetSocketAddress) {
      return io.vertx.core.net.SocketAddress.inetSocketAddress((InetSocketAddress) address);
    } else {
      return null;
    }
  }

  IoHandlerFactory ioHandlerFactory();

  /**
   * @param type one of {@link #ACCEPTOR_EVENT_LOOP_GROUP} or {@link #IO_EVENT_LOOP_GROUP}.
   * @param nThreads the number of threads that will be used by this instance.
   * @param threadFactory the ThreadFactory to use.
   * @param ioRatio the IO ratio
   *
   * @return a new event loop group
   */
  default EventLoopGroup eventLoopGroup(int type, int nThreads, ThreadFactory threadFactory, int ioRatio) {
    return new MultiThreadIoEventLoopGroup(nThreads, threadFactory, ioHandlerFactory());
  }

  /**
   * @return a new datagram channel
   */
  DatagramChannel datagramChannel(InternetProtocolFamily family);

  /**
   * @return the datagram channel
   */
  ChannelFactory<? extends DatagramChannel> datagramChannelFactory();

  /**
   * @return the suitable factory for TCP channels
   * @param domainSocket whether to create a unix domain socket channel or a TCP socket channel
   */
  ChannelFactory<? extends Channel> channelFactory(boolean domainSocket);

  /**
   * @return the suitable factory for TCP server channels
   * @param domainSocket whether to create a unix domain server socket channel or a TCP server socket channel
   */
  ChannelFactory<? extends ServerChannel> serverChannelFactory(boolean domainSocket);

  default void configure(DatagramChannel channel, DatagramSocketOptions options) {
    if (options.getSendBufferSize() != -1) {
      channel.config().setSendBufferSize(options.getSendBufferSize());
    }
    if (options.getReceiveBufferSize() != -1) {
      channel.config().setReceiveBufferSize(options.getReceiveBufferSize());
      channel.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(options.getReceiveBufferSize()));
    }
    channel.config().setOption(ChannelOption.SO_REUSEADDR, options.isReuseAddress());
    if (options.getTrafficClass() != -1) {
      channel.config().setTrafficClass(options.getTrafficClass());
    }
    channel.config().setBroadcast(options.isBroadcast());
    if (this instanceof NioTransport) {
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

  default void configure(TcpOptions options, boolean domainSocket, Bootstrap bootstrap) {
    if (!domainSocket) {
      bootstrap.option(ChannelOption.TCP_NODELAY, options.isTcpNoDelay());
      bootstrap.option(ChannelOption.SO_KEEPALIVE, options.isTcpKeepAlive());
    }
    if (options.getSoLinger() != -1) {
      bootstrap.option(ChannelOption.SO_LINGER, options.getSoLinger());
    }
  }

  default void configure(TcpOptions options, boolean domainSocket, ServerBootstrap bootstrap) {
    if (!domainSocket) {
      bootstrap.childOption(ChannelOption.TCP_NODELAY, options.isTcpNoDelay());
      bootstrap.childOption(ChannelOption.SO_KEEPALIVE, options.isTcpKeepAlive());
    }
    if (options.getSoLinger() != -1) {
      bootstrap.childOption(ChannelOption.SO_LINGER, options.getSoLinger());
    }
  }
}
