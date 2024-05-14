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
package io.vertx.core.spi.endpoint;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.Address;
import io.vertx.core.net.SocketAddress;

/**
 * Endpoint resolver Service Provider Interface (SPI).
 *
 * <p> {@link #resolve)} resolves an address to resolver managed state {@code <S>}. State modifying methods can be called
 * concurrently, the implementation is responsible to manage the concurrent state modifications.
 *
 * @param <D> the type of the state managed by the resolver
 * @param <A> the type of {@link Address} resolved
 * @param <S> the type of the server
 * @param <E> the type of the endpoint
 */
public interface EndpointResolver<A extends Address, S, D, E> {

  /**
   * Try to cast the {@code address} to an address instance that can be resolved by this resolver instance.
   *
   * @param address the address to cast
   * @return the address or {@code null} when the {@code address} cannot be resolved by this resolver
   */
  A tryCast(Address address);

  /**
   * Returns the socket address of a given {@code endpoint}.
   *
   * @param server the endpoint
   * @return the endpoint socket address
   */
  SocketAddress addressOf(S server);

  /**
   * Returns the known properties of a give {@code server}.
   *
   * @param server the endpoint
   * @return the properties as a JSON object
   */
  default JsonObject propertiesOf(S server) {
    return new JsonObject();
  }

  /**
   * Resolve an address to the resolver state for this name.
   *
   * @param address the address to resolve
   * @param builder the endpoint builder
   * @return a future notified with the result
   */
  Future<D> resolve(A address, EndpointBuilder<E, S> builder);

  /**
   * Return the current list of endpoint visible by the resolver.
   *
   * @param data the resolver state
   * @return the list of endpoints
   */
  E endpoint(D data);

  /**
   * Check the state validity.
   *
   * @param data resolver state
   * @return the state validity
   */
  boolean isValid(D data);

  /**
   * Dispose the state.
   *
   * @param data the state
   */
  void dispose(D data);

  /**
   * Close this resolver.
   */
  void close();

}
