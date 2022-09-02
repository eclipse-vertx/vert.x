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

package io.vertx5.core.impl.transport;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.*;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.channel.socket.nio.NioSocketChannel;
import io.vertx5.core.net.ClientOptionsBase;
import io.vertx5.core.net.NetServerOptions;
import io.vertx5.core.net.impl.SocketAddressImpl;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * The transport used by a {@link io.vertx.core.Vertx} instance.
 * <p/>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Transport {

  public static final int ACCEPTOR_EVENT_LOOP_GROUP = 0;
  public static final int IO_EVENT_LOOP_GROUP = 1;

  /**
   * The JDK transport, always there.
   */
  public static Transport JDK = new Transport();

  public static Transport transport() {
    return Transport.JDK;
  }

  protected Transport() {
  }

  /**
   * @return true when the transport is available.
   */
  public boolean isAvailable() {
    return true;
  }

  /**
   * @return the error that cause the unavailability when {@link #isAvailable()} returns {@code null}.
   */
  public Throwable unavailabilityCause() {
    return null;
  }

  public SocketAddress convert(io.vertx.core.net.SocketAddress address) {
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

  public io.vertx.core.net.SocketAddress convert(SocketAddress address) {
    if (address instanceof InetSocketAddress) {
      return io.vertx.core.net.SocketAddress.inetSocketAddress((InetSocketAddress) address);
    } else {
      return null;
    }
  }

  /**
   * Return a channel option for given {@code name} or null if that options does not exist
   * for this transport.
   *
   * @param name the option name
   * @return the channel option
   */
  ChannelOption<?> channelOption(String name) {
    return null;
  }

  /**
   * @return the type for channel
   * @param domainSocket whether to create a unix domain channel or a socket channel
   */
  public ChannelFactory<? extends Channel> channelFactory() {
    return NioSocketChannel::new;
  }

  /**
   * @return the type for server channel
   * @param domainSocket whether to create a server unix domain channel or a regular server socket channel
   */
  public ChannelFactory<? extends ServerChannel> serverChannelFactory(EventLoop childEventLoop) {
    return new ChannelFactory<ServerChannel>() {
      @Override
      public ServerChannel newChannel(EventLoop eventLoop) throws Exception {
        return new NioServerSocketChannel(eventLoop, childEventLoop);
      }
    };
  }

  public void configure(ClientOptionsBase options, boolean domainSocket, Bootstrap bootstrap) {
    if (!domainSocket) {
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
      // bootstrap.option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(options.getReceiveBufferSize()));
    }
    if (options.getSoLinger() != -1) {
      bootstrap.option(ChannelOption.SO_LINGER, options.getSoLinger());
    }
    if (options.getTrafficClass() != -1) {
      bootstrap.option(ChannelOption.IP_TOS, options.getTrafficClass());
    }
    bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, options.getConnectTimeout());
  }

  public void configure(NetServerOptions options, boolean domainSocket, ServerBootstrap bootstrap) {
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
      // bootstrap.childOption(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(options.getReceiveBufferSize()));
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
}
