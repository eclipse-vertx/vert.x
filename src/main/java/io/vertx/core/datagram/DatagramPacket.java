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
package io.vertx.core.datagram;

import io.vertx.core.buffer.Buffer;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.net.SocketAddress;

/**
 * A received datagram packet (UDP) which contains the data and information about the sender of the data itself.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@VertxGen
public interface DatagramPacket {

  /**
   * Returns the {@link io.vertx.core.net.SocketAddress} of the sender that sent
   * this {@link io.vertx.core.datagram.DatagramPacket}.
   *
   * @return the address of the sender
   */
  SocketAddress sender();

  /**
   * Returns the data of the {@link io.vertx.core.datagram.DatagramPacket}
   *
   * @return the data
   */
  Buffer data();
}
