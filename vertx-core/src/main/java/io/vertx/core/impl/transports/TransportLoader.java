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
package io.vertx.core.impl.transports;

import io.vertx.core.transport.Transport;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TransportLoader {

  public static Transport epoll() {
    try {
      EpollTransport impl = new EpollTransport();
      boolean available = impl.isAvailable();
      Throwable unavailabilityCause = impl.unavailabilityCause();
      return new TransportInternal("epoll", available, unavailabilityCause, impl);
    } catch (Throwable ignore) {
      // Jar not here
    }
    return null;
  }

  public static Transport io_uring() {
    try {
      IoUringTransport impl = new IoUringTransport();
      boolean available = impl.isAvailable();
      Throwable unavailabilityCause = impl.unavailabilityCause();
      return new TransportInternal("io_uring", available, unavailabilityCause, impl);
    } catch (Throwable ignore) {
      // Jar not here
    }
    return null;
  }

  public static Transport kqueue() {
    try {
      KQueueTransport impl = new KQueueTransport();
      boolean available = impl.isAvailable();
      Throwable unavailabilityCause = impl.unavailabilityCause();
      return new TransportInternal("kqueue", available, unavailabilityCause, impl);
    } catch (Throwable ignore) {
      // Jar not here
    }
    return null;
  }
}
