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
package io.vertx.core.spi.net;

import io.vertx.core.Future;
import io.vertx.core.net.Address;
import io.vertx.core.net.SocketAddress;

/**
 * Name resolver Service Provider Interface (SPI).
 *
 * <p> {@link #resolve(Address)} resolves an address to resolver managed state {@code <S>}. Such state can be queried
 * and mutated by the resolver, e.g. {@link #pickEndpoint(Object)} chooses an {@code SocketAddress} address and might
 * update the provided state. State modifying methods can be called concurrently, the implementation is responsible
 * to manage the concurrent state modifications.
 *
 * @param <S> the type of the state managed by the resolver
 * @param <A> the type of {@link Address} resolved
 * @param <M> the type of metrics, implementations can use {@code Void} when metrics are not managed
 * @param <E> the type of the endpoint
 */
public interface AddressResolver<S, A extends Address, M, E> {

  /**
   * Try to cast the {@code address} to an address instance that can be resolved by this resolver instance.
   *
   * @param address the address to cast
   * @return the address or {@code null} when the {@code address} cannot be resolved by this resolver
   */
  A tryCast(Address address);

  /**
   * Resolve an address to the resolver state for this name.
   *
   * @param address the address to resolve
   * @return a future notified with the result
   */
  Future<S> resolve(A address);

  /**
   * Check the state validity.
   *
   * @param state the state to check
   * @return whether the state is valid
   */
  default boolean isValid(S state) {
    return true;
  }

  /**
   * Pick an endpoint for the state.
   *
   * todo: make this synchronous
   *
   * @param state the state
   * @return the resolved socket address
   */
  Future<E> pickEndpoint(S state);

  /**
   * Returns the socket address of a given {@code endpoint}.
   *
   * @param endpoint the endpoint
   * @return the endpoint socket address
   */
  SocketAddress addressOf(E endpoint);

  /**
   * Remove a stale address from the state.
   *
   * @param state the state to update
   * @param endpoint the stale endpoint
   */
  void removeAddress(S state, E endpoint);

  /**
   * Dispose the state.
   *
   * @param state the state
   */
  void dispose(S state);

  /**
   * Signal the beginning of a request operated by the client
   * @param endpoint the endpoint to which the request is made
   * @return the request/response metric
   */
  default M requestBegin(E endpoint) {
    return null;
  }

  /**
   * Signal the end of the request attached to the {@code metric}
   * @param metric the request/response metric
   */
  default void requestEnd(M metric)  {}

  /**
   * Signal the beginning of the response attached to the {@code metric}
   * @param metric the request/response metric
   */
  default void responseBegin(M metric)  {}

  /**
   * Signal the end of the response attached to the {@code metric}
   * @param metric the request metric
   */
  default void responseEnd(M metric) {}
}
