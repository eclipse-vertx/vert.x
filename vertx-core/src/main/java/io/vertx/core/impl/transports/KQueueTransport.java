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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.kqueue.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.unix.DomainSocketAddress;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.core.spi.transport.Transport;

import java.net.SocketAddress;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class KQueueTransport implements Transport {

  public KQueueTransport() {
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
    return KQueue.isAvailable();
  }

  @Override
  public Throwable unavailabilityCause() {
    return KQueue.unavailabilityCause();
  }

  @Override
  public IoHandlerFactory ioHandlerFactory() {
    return KQueueIoHandler.newFactory();
  }

  @Override
  public DatagramChannel datagramChannel() {
    return new KQueueDatagramChannel();
  }

  @Override
  public DatagramChannel datagramChannel(InternetProtocolFamily family) {
    return new KQueueDatagramChannel();
  }

  @Override
  public ChannelFactory<? extends Channel> channelFactory(boolean domainSocket) {
    if (domainSocket) {
      return KQueueDomainSocketChannel::new;
    } else {
      return KQueueSocketChannel::new;
    }
  }

  @Override
  public ChannelFactory<? extends ServerChannel> serverChannelFactory(boolean domainSocket) {
    if (domainSocket) {
      return KQueueServerDomainSocketChannel::new;
    } else {
      return KQueueServerSocketChannel::new;
    }
  }

  @Override
  public void configure(NetServerOptions options, boolean domainSocket, ServerBootstrap bootstrap) {
    if (!domainSocket) {
      bootstrap.option(KQueueChannelOption.SO_REUSEPORT, options.isReusePort());
    }
    Transport.super.configure(options, domainSocket, bootstrap);
  }

  @Override
  public void configure(DatagramChannel channel, DatagramSocketOptions options) {
    channel.config().setOption(KQueueChannelOption.SO_REUSEPORT, options.isReusePort());
    Transport.super.configure(channel, options);
  }
}
