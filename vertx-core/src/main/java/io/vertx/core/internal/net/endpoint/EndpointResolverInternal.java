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
package io.vertx.core.internal.net.endpoint;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.Address;
import io.vertx.core.net.endpoint.Endpoint;
import io.vertx.core.net.endpoint.EndpointResolver;
import io.vertx.core.net.endpoint.LoadBalancer;
import io.vertx.core.net.endpoint.impl.EndpointResolverImpl;

public interface EndpointResolverInternal extends EndpointResolver {

  static EndpointResolverInternal create(VertxInternal vertx,
                                         io.vertx.core.spi.endpoint.EndpointResolver<?, ?, ?, ?> endpointResolver,
                                         LoadBalancer loadBalancer,
                                         long expirationMillis) {
    return new EndpointResolverImpl<>(vertx, endpointResolver, loadBalancer, expirationMillis);
  }

  void lookupEndpoint(Address address, Promise<Endpoint> promise);

  /**
   * Check expired endpoints, this method is called by the client periodically to give the opportunity to trigger eviction
   * or refreshes.
   */
  void checkExpired();


}
