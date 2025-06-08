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

import io.vertx.codegen.annotations.Unstable;
import io.vertx.core.Vertx;
import io.vertx.core.net.impl.MappingResolver;
import io.vertx.core.spi.endpoint.EndpointResolver;

import java.util.List;
import java.util.function.Function;

/**
 * A provider for address resolver.
 */
@Unstable
public interface AddressResolver<A extends Address> {

  /**
   * A simple synchronous resolver for demo and testing purposes.
   *
   * <p>The returned resolver uses the {@code mapping} function to obtain a list of endpoints for a given
   * address. The resolver calls the mapping function when it needs to select an endpoint node.</p>
   *
   * <p>The load balancing state of a given address is reset when the list of endpoints returned by the function
   * has changed. Therefore, when the function returns the same list of endpoints, the load balancing state is
   * preserved.</p>
   *
   * <p>When the {@code mapping} returns {@code null} or an empty list, implies the endpoint is not valid anymore and
   * fails the server selection.</p>
   *
   * @param mapping the mapping function
   * @return an address resolver
   */
  static <A extends Address> AddressResolver<A> mappingResolver(Function<A, List<SocketAddress>> mapping) {
    return vertx -> new MappingResolver<>(mapping);
  }

  /**
   * Return a resolver capable of resolving addresses to endpoints.
   *
   * @param vertx the vertx instance
   * @return the resolver
   */
  EndpointResolver<A, ?, ?, ?> endpointResolver(Vertx vertx);

}
