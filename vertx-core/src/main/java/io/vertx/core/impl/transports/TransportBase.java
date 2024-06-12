/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.impl.transports;

import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.socket.DatagramChannel;
import io.vertx.core.buffer.impl.PartialPooledByteBufAllocator;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.spi.transport.Transport;

import java.net.NetworkInterface;
import java.net.SocketException;

public abstract class TransportBase implements Transport {

  @Override
  public void configure(DatagramChannel channel, DatagramSocketOptions options) {
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
}
