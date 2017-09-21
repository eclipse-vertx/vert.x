/*
 * Copyright (c) 2011-2014 The original author or authors
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
