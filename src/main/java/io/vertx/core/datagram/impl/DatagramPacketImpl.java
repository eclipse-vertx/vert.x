/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 *     The Apache License v2.0 is available at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * You may elect to redistribute this code under either of these licenses.
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
