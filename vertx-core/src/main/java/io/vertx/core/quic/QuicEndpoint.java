/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.quic;

import io.vertx.core.Future;
import io.vertx.core.metrics.Measured;
import io.vertx.core.net.SocketAddress;

/**
 * Represents a Quic endpoints, whose primary function is to bind a UDP socket on a given or random port.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface QuicEndpoint extends Measured {

  /**
   * Binds a UDP socket at the given {@code address}.
   *
   * <p>>When the endpoint is successfully bound, a</p
   *
   * <ul>
   *   <li>{@link QuicClient} can connect to a Quic server</li>
   *   <li>{@link QuicServer} can accept connections from a Quic client</li>
   * </ul>
   *
   * @param address the bind address
   * @return a future signaling the success or failure of the bind operation
   */
  Future<Void> bind(SocketAddress address);

  /**
   * Close the endpoint and release all associated resources.
   *
   * @return a future signaling the result
   */
  Future<Void> close();

}
