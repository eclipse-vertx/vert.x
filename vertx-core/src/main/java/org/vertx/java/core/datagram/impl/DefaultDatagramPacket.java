/*
 * Copyright (c) 2011-2013 The original author or authors
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
package org.vertx.java.core.datagram.impl;

import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.datagram.DatagramPacket;

import java.net.InetSocketAddress;


/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class DefaultDatagramPacket implements DatagramPacket {
  private final InetSocketAddress sender;
  private final Buffer buffer;

  DefaultDatagramPacket(InetSocketAddress sender, Buffer buffer) {
    this.sender = sender;
    this.buffer = buffer;
  }

  @Override
  public InetSocketAddress sender() {
    return sender;
  }

  @Override
  public Buffer data() {
    return buffer;
  }
}
