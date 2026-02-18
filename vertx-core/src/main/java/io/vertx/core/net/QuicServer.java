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

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.net.impl.SocketAddressImpl;

/**
 * A Quic server.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface QuicServer extends QuicEndpoint {

  /**
   * Set the handler processing {@link QuicConnection}, the handler must be set before the server is bound.
   * @param handler the connection handler
   * @return this object instance
   */
  QuicServer handler(Handler<QuicConnection> handler);

  /**
   * Start listening on the {@code port} and {@code host} as configured in the {@link io.vertx.core.net.QuicServerConfig} used when
   * creating the server.
   *
   * @return a future completed with the listen operation result
   */
  Future<Integer> listen();

  /**
   * Start listening on the specified {@code port} and {@code host}.
   * <p>
   * Port {@code 0} can be specified meaning "choose a random port".
   * <p>
   * Host {@code 0.0.0.0} can be specified meaning "listen on all available interfaces".
   *
   * @return a future completed with the port the server is bound to
   */
  default Future<Integer> listen(int port, String host) {
    return listen(new SocketAddressImpl(port, host));
  }

  /**
   * Start listening on the specified port and host "0.0.0.0".
   * <p>
   * Port {@code 0} can be specified meaning "choose an random port".
   *
   * @return a future completed with the port the server is bound to
   */
  default Future<Integer> listen(int port) {
    return listen(port, "0.0.0.0");
  }

  /**
   * Start listening on the specified local address.
   *
   * @param localAddress the local address to listen on
   * @return a future completed with the port the server is bound to
   */
  default Future<Integer> listen(SocketAddress localAddress) {
    return bind(localAddress);
  }

}
