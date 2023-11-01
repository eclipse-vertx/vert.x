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

import io.vertx.core.Future;
import io.vertx.core.loadbalancing.EndpointSelector;
import io.vertx.core.loadbalancing.EndpointMetrics;
import io.vertx.core.loadbalancing.LoadBalancer;
import io.vertx.core.net.Address;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.net.AddressResolver;
import io.vertx.core.spi.net.Endpoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolver implementation.
 *
 * @param <S> the type of the state managed by the resolver
 * @param <A> the type of {@link Address} resolved
 * @param <E> the type of the endpoint
 */
final class ResolverImpl<A extends Address, E, S> {

  private final LoadBalancer loadBalancer;
  private final AddressResolver<A, E, S> plugin;

  ResolverImpl(LoadBalancer loadBalancer, AddressResolver<A, E, S> plugin) {

    if (loadBalancer == null) {
      loadBalancer = LoadBalancer.ROUND_ROBIN;
    }

    this.loadBalancer = loadBalancer;
    this.plugin = plugin;
  }

  /**
   * Try to cast the {@code address} to an address instance that can be resolved by this resolver instance.
   *
   * @param address the address to cast
   * @return the address or {@code null} when the {@code address} cannot be resolved by this resolver
   */
  public A tryCast(Address address) {
    return plugin.tryCast(address);
  }

  /**
   * Resolve an address to the resolver state for this name.
   *
   * @param address the address to resolve
   * @return a future notified with the result
   */
  public Future<ManagedState<S>> resolve(A address) {
    EndpointSelector selector = loadBalancer.selector();
    return plugin.resolve(e -> new ManagedEndpoint<>(e, selector.endpointMetrics()), address)
      .map(s -> new ManagedState<>(selector, s));
  }

  /**
   * Dispose the state.
   *
   * @param state the state
   */
  public void dispose(S state) {
    plugin.dispose(state);
  }

  public boolean isValid(S state) {
    return plugin.isValid(state);
  }

  /**
   * Pick an endpoint for the state.
   *
   * @param state the state
   * @return the resolved socket address
   */
  public ManagedEndpoint<E> pickEndpoint(ManagedState<S> state) {

    List<Endpoint<E>> lst = plugin.endpoints(state.state);
    List<EndpointMetrics<?>> other = new ArrayList<>();
    // AVOID THAT
    for (int i = 0;i < lst.size();i++) {
      other.add(((ManagedEndpoint)lst.get(i)).endpoint);
    }
    int idx = state.selector.selectEndpoint(other);
    if (idx >= 0 && idx < lst.size()) {
      return (ManagedEndpoint<E>) lst.get(idx);
    }
    throw new UnsupportedOperationException("TODO");
  }

  /**
   * Returns the socket address of a given {@code endpoint}.
   *
   * @param endpoint the endpoint
   * @return the endpoint socket address
   */
  public SocketAddress addressOf(ManagedEndpoint<E> endpoint) {
    return plugin.addressOfEndpoint(endpoint.value);
  }
}
