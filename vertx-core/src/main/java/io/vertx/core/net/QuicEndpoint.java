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
package io.vertx.core.net;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.metrics.Measured;

import java.time.Duration;

/**
 * Represents a Quic endpoints, whose primary function is to bind a UDP socket on a given or random port.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
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
   * @return a future signaling the success or failure of the bind operation, the future completion is the bound this
   *         endpoint was bound to
   */
  Future<Integer> bind(SocketAddress address);

  /**
   * Close the endpoint and release all associated resources.
   *
   * @return a future completed with the close operation result
   */
  default Future<Void> close() {
    return shutdown(Duration.ZERO);
  }

  /**
   * Shutdown the endpoint with a 30 seconds grace period ({@code shutdown(30, TimeUnit.SECONDS)}).
   *
   * @return a future completed when shutdown has completed
   */
  default Future<Void> shutdown() {
    return shutdown(Duration.ofSeconds(30));
  }

  /**
   * Initiate the endpoint shutdown sequence.
   *
   * @return a future notified when the client is closed
   * @param timeout the amount of time after which all resources are forcibly closed
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  Future<Void> shutdown(Duration timeout);

}
