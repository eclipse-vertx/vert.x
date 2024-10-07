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

public final class TransportInternal implements Transport {

  private final String name;
  private final boolean available;
  private final Throwable unavailabilityCause;
  private final io.vertx.core.spi.transport.Transport implementation;

  public TransportInternal(String name,
                           boolean available,
                           Throwable unavailabilityCause,
                           io.vertx.core.spi.transport.Transport implementation) {
    this.name = name;
    this.available = available;
    this.unavailabilityCause = unavailabilityCause;
    this.implementation = implementation;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public boolean available() {
    return available;
  }

  @Override
  public Throwable unavailabilityCause() {
    return unavailabilityCause;
  }

  /**
   * @return the transport implementation
   */
  public io.vertx.core.spi.transport.Transport implementation() {
    return implementation;
  }
}
