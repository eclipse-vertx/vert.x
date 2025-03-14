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
package io.vertx.core.net.impl;

import io.vertx.core.Future;
import io.vertx.core.net.Address;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.endpoint.EndpointBuilder;
import io.vertx.core.spi.endpoint.EndpointResolver;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class MappingResolver<B> implements EndpointResolver<Address, SocketAddress, MappingLookup<B>, B> {

  private final Function<Address, List<SocketAddress>> serviceMap;

  public MappingResolver(Function<Address, List<SocketAddress>> serviceMap) {
    this.serviceMap = Objects.requireNonNull(serviceMap);
  }

  @Override
  public Address tryCast(Address address) {
    return address;
  }

  @Override
  public SocketAddress addressOf(SocketAddress server) {
    return server;
  }

  @Override
  public Future<MappingLookup<B>> resolve(Address address, EndpointBuilder<B, SocketAddress> builder) {
    return Future.succeededFuture(new MappingLookup<>(address, builder));
  }

  @Override
  public B endpoint(MappingLookup<B> state) {
    List<SocketAddress> endpoints = serviceMap.apply(state.address);
    synchronized (state) {
      if (!Objects.equals(state.endpoints, endpoints)) {
        EndpointBuilder<B, SocketAddress> builder = state.builder;
        if (endpoints != null) {
          for (SocketAddress address : endpoints) {
            builder = builder.addServer(address);
          }
        }
        state.endpoints = endpoints;
        state.list = builder.build();
      }
      return state.list;
    }
  }

  @Override
  public boolean isValid(MappingLookup<B> state) {
    return true;
  }

  @Override
  public void dispose(MappingLookup<B> data) {
  }

  @Override
  public void close() {
  }
}
