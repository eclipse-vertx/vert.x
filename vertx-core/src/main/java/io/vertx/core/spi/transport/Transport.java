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
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.ClientOptionsBase;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.buffer.impl.PartialPooledByteBufAllocator;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.core.impl.transports.JDKTransport;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
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
      throw new IllegalArgumentException("Domain socket are not supported by JDK transport, you need to use native transport to use them");
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

  /**
   * @param type one of {@link #ACCEPTOR_EVENT_LOOP_GROUP} or {@link #IO_EVENT_LOOP_GROUP}.
   * @param nThreads the number of threads that will be used by this instance.
   * @param threadFactory the ThreadFactory to use.
   * @param ioRatio the IO ratio
   *
   * @return a new event loop group
   */
  EventLoopGroup eventLoopGroup(int type, int nThreads, ThreadFactory threadFactory, int ioRatio);

  /**
   * @return a new datagram channel
   */
  DatagramChannel datagramChannel();

  /**
   * @return a new datagram channel
   */
  DatagramChannel datagramChannel(InternetProtocolFamily family);

  /**
   * @return the type for channel
   * @param domainSocket whether to create a unix domain channel or a socket channel
   */
  ChannelFactory<? extends Channel> channelFactory(boolean domainSocket);

  /**
   * @return the type for server channel
   * @param domainSocket whether to create a server unix domain channel or a regular server socket channel
   */
  ChannelFactory<? extends ServerChannel> serverChannelFactory(boolean domainSocket);

  default void configure(DatagramChannel channel, DatagramSocketOptions options) {
    channel.config().setAllocator(PartialPooledByteBufAllocator.INSTANCE);
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
    if (this instanceof JDKTransport) {
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

  default void configure(ClientOptionsBase options, int connectTimeout, boolean domainSocket, Bootstrap bootstrap) {
    if (!domainSocket && options.getProtocolVersion() != HttpVersion.HTTP_3) {
      bootstrap.option(ChannelOption.SO_REUSEADDR, options.isReuseAddress());
      bootstrap.option(ChannelOption.TCP_NODELAY, options.isTcpNoDelay());
      bootstrap.option(ChannelOption.SO_KEEPALIVE, options.isTcpKeepAlive());
    }
    if (options.getLocalAddress() != null) {
      bootstrap.localAddress(options.getLocalAddress(), 0);
    }
    if (options.getSendBufferSize() != -1) {
      bootstrap.option(ChannelOption.SO_SNDBUF, options.getSendBufferSize());
    }
    if (options.getReceiveBufferSize() != -1) {
      bootstrap.option(ChannelOption.SO_RCVBUF, options.getReceiveBufferSize());
      bootstrap.option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(options.getReceiveBufferSize()));
    }
    if (options.getSoLinger() != -1) {
      bootstrap.option(ChannelOption.SO_LINGER, options.getSoLinger());
    }
    if (options.getTrafficClass() != -1) {
      bootstrap.option(ChannelOption.IP_TOS, options.getTrafficClass());
    }
    bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout);
  }

  default void configure(NetServerOptions options, boolean domainSocket, ServerBootstrap bootstrap) {
    bootstrap.option(ChannelOption.SO_REUSEADDR, options.isReuseAddress());
    if (!domainSocket) {
      bootstrap.childOption(ChannelOption.SO_KEEPALIVE, options.isTcpKeepAlive());
      bootstrap.childOption(ChannelOption.TCP_NODELAY, options.isTcpNoDelay());
    }
    if (options.getSendBufferSize() != -1) {
      bootstrap.childOption(ChannelOption.SO_SNDBUF, options.getSendBufferSize());
    }
    if (options.getReceiveBufferSize() != -1) {
      bootstrap.childOption(ChannelOption.SO_RCVBUF, options.getReceiveBufferSize());
      bootstrap.childOption(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(options.getReceiveBufferSize()));
    }
    if (options.getSoLinger() != -1) {
      bootstrap.childOption(ChannelOption.SO_LINGER, options.getSoLinger());
    }
    if (options.getTrafficClass() != -1) {
      bootstrap.childOption(ChannelOption.IP_TOS, options.getTrafficClass());
    }
    if (options.getAcceptBacklog() != -1) {
      bootstrap.option(ChannelOption.SO_BACKLOG, options.getAcceptBacklog());
    }
  }

  default void configure(NetServerOptions options, Bootstrap serverBootstrap) {
    if (options.getAcceptBacklog() != -1) {
      serverBootstrap.option(ChannelOption.SO_BACKLOG, options.getAcceptBacklog());
    }
  }
}
