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
package io.vertx.core.net.impl.resolver;

import io.vertx.core.spi.loadbalancing.EndpointMetrics;
import io.vertx.core.spi.resolver.address.Endpoint;

final class ManagedEndpoint<E> implements Endpoint<E> {

  final E value;
  final EndpointMetrics<?> endpoint;

  ManagedEndpoint(E value, EndpointMetrics<?> endpoint) {
    this.value = value;
    this.endpoint = endpoint;
  }

  @Override
  public E get() {
    return value;
  }
}
