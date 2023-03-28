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
package io.vertx.core.net.impl.pool;

import io.vertx.core.Future;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.Address;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.resolver.AddressResolver;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A {@link ConnectionManager} decorator that resolves a socket address from a name string.
 *
 * @param <S> the resolver state type
 * @param <K> the resolution key type
 * @param <C> the connection type
 * @param <A> the resolved address type
 */
public class EndpointResolver<S, K, C, A extends Address> extends ConnectionManager<ResolvingKey<K, A>, C> {

  private final AddressResolver<S, A, ?> resolver;

  public EndpointResolver(EndpointProvider<K, C> provider, AddressResolver<S, A, ?> endpointProvider, BiFunction<K, SocketAddress, K> resolver) {
    super(new EndpointProvider<>() {
      @Override
      public Endpoint<C> create(ResolvingKey<K, A> key, Runnable dispose) {
        ConnectionManager<EndpointKey<K>, C> connectionManager = new ConnectionManager<>((key_, dispose_) -> {
          class Disposer implements Runnable {
            @Override
            public void run() {
              key_.cleanup();
              dispose_.run();
            }
          }
          return provider.create(resolver.apply(key_.key, key_.address), new Disposer());
        });
        return new ResolvedEndpoint<>(endpointProvider.resolve(key.address), endpointProvider, dispose, connectionManager, key);
      }
    });

    this.resolver = endpointProvider;
  }

  /**
   * Try to cast the {@code address} to the resolver address and then resolve the couple (address,key) as an
   * endpoint, the {@code function} is then applied on the endpoint and the value returned.
   *
   * @param address the address to resolve
   * @param key the endpoint key
   * @param function the function to apply on the endpoint
   * @return the value returned by the function when applied on the resolved endpoint.
   */
  public <T> T withEndpoint(Address address, K key, Function<Endpoint<C>, Optional<T>> function) {
    A resolverAddress = resolver.tryCast(address);
    if (resolverAddress == null) {
      return null;
    } else {
      return withEndpoint(new ResolvingKey<>(key, resolverAddress), function);
    }
  }

  public static class ResolvedEndpoint<S, A extends Address, K, C> extends Endpoint<C> {

    private final AtomicReference<S> state;
    private final Future<S> resolved;
    private final AddressResolver<S, A, ?> resolver;
    private final ConnectionManager<EndpointKey<K>, C> connectionManager;
    private final ResolvingKey<K, A> key;

    public ResolvedEndpoint(Future<S> resolved, AddressResolver<S, A, ?> resolver, Runnable dispose, ConnectionManager<EndpointKey<K>, C> connectionManager, ResolvingKey<K, A> key) {
      super(() -> {
        if (resolved.result() != null) {
          resolver.dispose(resolved.result());
        }
        dispose.run();
      });
      AtomicReference<S> state = new AtomicReference<>();
      Future<S> fut = resolved.andThen(ar -> {
        if (ar.succeeded()) {
          state.set(ar.result());
        }
      });
      this.resolved = fut;
      this.state = state;
      this.resolver = resolver;
      this.connectionManager = connectionManager;
      this.key = key;
    }

    public S state() {
      return state.get();
    }

    public AddressResolver<S, A, ?> resolver() {
      return resolver;
    }

    @Override
    public void checkExpired() {
      connectionManager.checkExpired();
    }

    @Override
    public Future<C> requestConnection(ContextInternal ctx, long timeout) {
      return resolved.compose(state -> resolver
        .pickAddress(state)
        .compose(origin -> {
          incRefCount();
          return connectionManager.getConnection(ctx, new EndpointKey<>(origin, key.key) {
            @Override
            void cleanup() {
              resolver.removeAddress(state, origin);
              decRefCount();
            }
          }, timeout);
        }));
    }
  }

  private abstract static class EndpointKey<K> {
    final SocketAddress address;
    public EndpointKey(SocketAddress address, K key) {
      this.key = key;
      this.address = address;
    }
    final K key;
    abstract void cleanup();
    @Override
    public int hashCode() {
      return address.hashCode();
    }
    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj instanceof EndpointResolver.EndpointKey<?>) {
        EndpointKey<?> that = (EndpointKey<?>) obj;
        return address.equals(that.address);
      }
      return false;
    }
    @Override
    public String toString() {
      return "EndpointKey(address=" + address + ",key=" + key + ")";
    }
  }
}
