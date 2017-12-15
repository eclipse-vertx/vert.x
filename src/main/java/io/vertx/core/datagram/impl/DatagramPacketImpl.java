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

package io.vertx.core.datagram.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramPacket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.SocketAddressImpl;

import java.net.InetSocketAddress;


/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class DatagramPacketImpl implements DatagramPacket {
  private final InetSocketAddress sender;
  private final Buffer buffer;
  private SocketAddress senderAddress;

  DatagramPacketImpl(InetSocketAddress sender, Buffer buffer) {
    this.sender = sender;
    this.buffer = buffer;
  }

  @Override
  public SocketAddress sender() {
    if (senderAddress == null) {
      senderAddress = new SocketAddressImpl(sender.getPort(), sender.getAddress().getHostAddress());
    }
    return senderAddress;
  }

  @Override
  public Buffer data() {
    return buffer;
  }
}
