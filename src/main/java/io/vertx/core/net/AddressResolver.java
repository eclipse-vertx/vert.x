/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net;

import io.vertx.core.Vertx;

/**
 * A generic address resolver market interface. Implementation must also implement the SPI interface {@link io.vertx.core.spi.resolver.address.AddressResolver}
 * and can be cast to this type.
 */
public interface AddressResolver {

  /**
   * Return a resolver capable of resolving addresses for a client.
   *
   * @param vertx the vertx instance
   * @return the resolver
   */
  io.vertx.core.spi.resolver.address.AddressResolver<?, ?, ?> resolver(Vertx vertx);

}
