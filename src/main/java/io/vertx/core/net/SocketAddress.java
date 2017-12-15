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

package io.vertx.core.net;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.net.impl.SocketAddressImpl;

/**
 * The address of a socket, an inet socket address or a domain socket address.
 * <p/>
 * Use {@link #inetSocketAddress(int, String)} to create an inet socket address and {@link #domainSocketAddress(String)}
 * to create a domain socket address
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface SocketAddress {

  /**
   * Create a inet socket address, {@code host} must be non {@code null} and {@code port} must be between {@code 0}
   * and {@code 65536}.
   *
   * @param port the address port
   * @param host the address host
   * @return the created socket address
   */
  static SocketAddress inetSocketAddress(int port, String host) {
    return new SocketAddressImpl(port, host);
  }

  /**
   * Create a domain socket address.
   *
   * @param path the address path
   * @return the created socket address
   */
  static SocketAddress domainSocketAddress(String path) {
    return new SocketAddressImpl(path);
  }

  /**
   * @return the address host or {@code null} for a domain socket
   */
  String host();

  /**
   * @return the address port or {@code -1} for a domain socket
   */
  int port();

  /**
   * @return the address path or {@code null} for a inet socket
   */
  String path();

}
