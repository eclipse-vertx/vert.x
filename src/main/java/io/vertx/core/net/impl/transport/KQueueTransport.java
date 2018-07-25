/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net.impl.transport;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.kqueue.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.unix.DomainSocketAddress;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.net.NetServerOptions;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ThreadFactory;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class KQueueTransport extends Transport {

  KQueueTransport() {
  }

  @Override
  public SocketAddress convert(io.vertx.core.net.SocketAddress address, boolean resolved) {
    if (address.path() != null) {
      return new DomainSocketAddress(address.path());
    } else {
      if (resolved) {
        return new InetSocketAddress(address.host(), address.port());
      } else {
        return InetSocketAddress.createUnresolved(address.host(), address.port());
      }
    }
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
  public EventLoopGroup eventLoopGroup(int nThreads, ThreadFactory threadFactory, int ioRatio) {
    KQueueEventLoopGroup eventLoopGroup = new KQueueEventLoopGroup(nThreads, threadFactory);
    eventLoopGroup.setIoRatio(ioRatio);
    return eventLoopGroup;
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
  public ChannelFactory<? extends Channel> channelFactory(boolean domain) {
    if (domain) {
      return KQueueDomainSocketChannel::new;
    } else {
      return KQueueSocketChannel::new;
    }
  }

  @Override
  public ChannelFactory<? extends ServerChannel> serverChannelFactory(boolean domain) {
    if (domain) {
      return KQueueServerDomainSocketChannel::new;
    } else {
      return KQueueServerSocketChannel::new;
    }
  }

  @Override
  public void configure(NetServerOptions options, ServerBootstrap bootstrap) {
    bootstrap.option(KQueueChannelOption.SO_REUSEPORT, options.isReusePort());
    super.configure(options, bootstrap);
  }

  @Override
  public void configure(DatagramChannel channel, DatagramSocketOptions options) {
    channel.config().setOption(KQueueChannelOption.SO_REUSEPORT, options.isReusePort());
    super.configure(channel, options);
  }
}
