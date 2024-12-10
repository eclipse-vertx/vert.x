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

package io.vertx.core.datagram;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.SocketAddress;

/**
 * A received datagram packet (UDP) which contains the data and information about the sender of the data itself.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@DataObject
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
