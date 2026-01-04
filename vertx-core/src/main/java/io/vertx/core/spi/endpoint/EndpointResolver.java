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

import java.util.Collections;
import java.util.Map;

/**
 * Endpoint resolver Service Provider Interface (SPI).
 *
 * <p> {@link #resolve)} resolves an address to resolver managed state {@code <S>}. State modifying methods can be called
 * concurrently, the implementation is responsible to manage concurrent state modifications.
 *
 * @param <A> the type of {@link Address} resolved
 * @param <S> the type of the endpoint server
 * @param <D> the type of the data managed by the resolver
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
   * Returns the socket address of a given endpoint {@code server}.
   *
   * @param server the endpoint server
   * @return the server socket address
   */
  SocketAddress addressOf(S server);

  /**
   * Returns the known properties of a given {@code server}.
   *
   * @param server the endpoint
   * @return the properties as a JSON object
   */
  default Map<String, String> propertiesOf(S server) {
    return Collections.emptyMap();
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
   * Return the current endpoint visible by the resolver.
   *
   * @param state the resolver state
   * @return the list of endpoints
   */
  E endpoint(D state);

  /**
   * Check the state validity.
   *
   * @param state resolver state
   * @return the state validity
   */
  boolean isValid(D state);

  /**
   *
   * @param address
   * @param state
   * @return
   */
  default Future<D> refresh(A address, D state) {
    return null;
  }

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
