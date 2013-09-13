/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
