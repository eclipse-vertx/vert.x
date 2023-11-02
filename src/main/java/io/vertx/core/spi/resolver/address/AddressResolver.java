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
package io.vertx.core.spi.resolver.address;

import io.vertx.core.Future;
import io.vertx.core.net.Address;
import io.vertx.core.net.SocketAddress;

import java.util.List;
import java.util.function.Function;

/**
 * Address resolver Service Provider Interface (SPI).
 *
 * <p> {@link #resolve)} resolves an address to resolver managed state {@code <S>}. State modifying methods can be called concurrently, the implementation is responsible
 * to manage the concurrent state modifications.
 *
 * @param <S> the type of the state managed by the resolver
 * @param <A> the type of {@link Address} resolved
 * @param <E> the type of the endpoint
 */
public interface AddressResolver<A extends Address, E, S> {

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
   * @param endpoint the endpoint
   * @return the endpoint socket address
   */
  SocketAddress addressOfEndpoint(E endpoint);

  /**
   * Resolve an address to the resolver state for this name.
   *
   * @param factory the endpoint factory
   * @param address the address to resolve
   * @return a future notified with the result
   */
  Future<S> resolve(Function<E, Endpoint<E>> factory, A address);

  /**
   * Return the current list of endpoint visible by the resolver.
   *
   * @param state the resolver state
   * @return the list of endpoints
   */
  List<Endpoint<E>> endpoints(S state);

  /**
   * Check the state validity.
   *
   * @param state resolver state
   * @return the state validity
   */
  boolean isValid(S state);

  /**
   * Dispose the state.
   *
   * @param state the state
   */
  void dispose(S state);

  /**
   * Close this resolver.
   */
  void close();

}
