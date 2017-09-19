/*
 * Copyright (c) 2011-2017 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.spi.transport;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;

import java.net.SocketAddress;
import java.util.concurrent.ThreadFactory;

/**
 * The transport used by a {@link io.vertx.core.Vertx} instance.
 * <p/>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface Transport {

  /**
   * The JDK transport, always there.
   */
  Transport JDK = JdkTransport.INSTANCE;

  /**
   * The native transport, it may be {@code null} or failed.
   */
  static Transport nativeTransport() {
    Transport transport = null;
    try {
      Transport epoll = new io.vertx.core.spi.transport.EpollTransport();
      if (epoll.isAvailable()) {
        return epoll;
      } else {
        transport = epoll;
      }
    } catch (Throwable ignore) {
      // Jar not here
    }
    try {
      Transport kqueue = new io.vertx.core.spi.transport.KQeueTransport();
      if (kqueue.isAvailable()) {
        return kqueue;
      } else if (transport == null) {
        transport = kqueue;
      }
    } catch (Throwable ignore) {
      // Jar not here
    }
    return transport;
  }

  /**
   * @return true when the transport is available.
   */
  boolean isAvailable();

  /**
   * @return the error that cause the unavailability when {@link #isAvailable()} returns {@code null}.
   */
  Throwable unavailabilityCause();

  SocketAddress convert(io.vertx.core.net.SocketAddress address, boolean resolved);

  /**
   * Return a channel option for given {@code name} or null if that options does not exist
   * for this transport.
   *
   * @param name the option name
   * @return the channel option
   */
  ChannelOption<?> channelOption(String name);

  /**
   * @return a new event loop group
   */
  EventLoopGroup eventLoopGroup(int nThreads, ThreadFactory threadFactory, int ioRatio);

  /**
   * @return a new datagram channel
   */
  DatagramChannel datagramChannel();

  /**
   * @return a new datagram channel
   */
  DatagramChannel datagramChannel(InternetProtocolFamily family);

  /**
   * @return a new socket channel
   * @param domain wether to create a domain socket or a regular socket
   */
  Channel socketChannel(boolean domain);

  /**
   * @return a new server socket channel
   * @param domain wether to create a domain socket or a regular socket
   */
  ServerChannel serverChannel(boolean domain);

}
