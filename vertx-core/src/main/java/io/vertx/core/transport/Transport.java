/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.transport;

import io.vertx.core.impl.transports.NioTransport;
import io.vertx.core.impl.transports.TransportInternal;
import io.vertx.core.impl.transports.TransportLoader;

/**
 * The transport used by a {@link io.vertx.core.Vertx} instance.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface Transport {

  /**
   * Nio transport, always available based on ${code java.nio} API.
   */
  Transport NIO = new TransportInternal("nio", true, null, NioTransport.INSTANCE);

  /**
   * Native transport based on Netty native kqueue transport.
   */
  Transport KQUEUE = TransportLoader.kqueue();

  /**
   * Native transport based on Netty native epoll transport.
   */
  Transport EPOLL = TransportLoader.epoll();

  /**
   * Native transport based on Netty native io_uring transport.
   */
  Transport IO_URING = TransportLoader.io_uring();

  /**
   * @return the name among {@code nio, kqueue, epoll, io_uring}
   */
  String name();

  /**
   * Return a native transport suitable for the OS
   *
   * <ul>
   *   <li>{@link #EPOLL} on Linux</li>
   *   <li>{@link #KQUEUE} on Mac</li>
   * </ul>
   *
   * @return a native transport, it might return an unavailable transport ({@link Transport#available()}) then {@link Transport#unavailabilityCause()}
   * can be used to check the error preventing its usafe, {@code null} can be returned when no native transport can be loaded.
   */
  static Transport nativeTransport() {
    return KQUEUE != null ? KQUEUE : EPOLL;
  }

  /**
   * @return whether the transport can be used by a Vert.x instance
   */
  boolean available();

  /**
   * @return the unavailability cause when {#link {@link #available()}} returns true, otherwise {@code null}
   */
  Throwable unavailabilityCause();

  /**
   * @return the implementation
   */
  io.vertx.core.spi.transport.Transport implementation();
}
