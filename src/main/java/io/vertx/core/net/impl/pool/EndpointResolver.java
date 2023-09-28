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
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.net.Address;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.net.AddressResolver;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A {@link ConnectionManager} decorator that resolves a socket address from a name string.
 *
 * @param <S> the resolver state type
 * @param <K> the endpoint key type
 * @param <C> the connection type
 * @param <A> the resolved address type
 */
public class EndpointResolver<S, K, C, A extends Address, E> {

  private final AddressResolver<S, A, ?, E> addressResolver;
  private final ConnectionManager<A, ConnectionLookup<C, E, ?>> connectionManager;

  public EndpointResolver(AddressResolver<S, A, ?, E> addressResolver) {
    this.addressResolver = addressResolver;
    this.connectionManager = new ConnectionManager<>();
  }

  public void checkExpired() {
    connectionManager.checkExpired();
  }

  /**
   * Try to cast the {@code address} to the resolver address and then resolve the couple (address,key) as an
   * endpoint, the {@code function} is then applied on the endpoint and the value returned.
   *
   * @param address  the address to resolve
   * @param fn the function to apply on the endpoint
   * @return the value returned by the function when applied on the resolved endpoint.
   */
  public <T, P> T withEndpoint(Address address,
                            P payload,
                            EndpointProvider<K, C> endpointProvider,
                            BiFunction<P, SocketAddress, K> zfn,
                            Function<Endpoint<ConnectionLookup<C, E, ?>>, Optional<T>> fn) {
    A resolverAddress = addressResolver.tryCast(address);
    if (resolverAddress == null) {
      return null;
    } else {
      EndpointProvider<A, ConnectionLookup<C, E, ?>> provider = (key, disposer) -> new AddressEndpoint<>(
        addressResolver.resolve(key),
        disposer,
        key,
        endpointProvider,
        zfn);
      return connectionManager.withEndpoint(resolverAddress, provider, endpoint -> {
        thread_local.set(payload);
        try {
          return fn.apply(endpoint);
        } finally {
          thread_local.remove();
        }
      });
    }
  }

  // Trick to pass payload (as we have synchronous calls)
  final ThreadLocal<Object> thread_local = new ThreadLocal<>();

  public class AddressEndpoint<P> extends Endpoint<ConnectionLookup<C, E, ?>> {

    private final AtomicReference<S> state;
    private final AtomicReference<Future<S>> stateRef;
    private final ConnectionManager<K, C> connectionManager;
    private final A address;
    private final EndpointProvider<K, C> endpointProvider;
    private final BiFunction<P, SocketAddress, K> zfn;

    public AddressEndpoint(Future<S> stateRef, Runnable disposer, A address, EndpointProvider<K, C> endpointProvider, BiFunction<P, SocketAddress, K> zfn) {
      super(() -> {
        if (stateRef.result() != null) {
          addressResolver.dispose(stateRef.result());
        }
        disposer.run();
      });
      AtomicReference<S> state = new AtomicReference<>();
      Future<S> fut = stateRef.andThen(ar -> {
        if (ar.succeeded()) {
          state.set(ar.result());
        }
      });
      this.stateRef = new AtomicReference<>(fut);
      this.state = state;
      this.connectionManager = new ConnectionManager<>();
      this.address = address;
      this.endpointProvider = endpointProvider;
      this.zfn = zfn;
    }

    public S state() {
      return state.get();
    }

    @Override
    public void checkExpired() {
      connectionManager.checkExpired();
    }

    @Override
    public Future<ConnectionLookup<C, E, ?>> requestConnection(ContextInternal ctx, long timeout) {
      P payload = (P) thread_local.get();
      Future<S> fut = stateRef.get();
      return fut.transform(ar -> {
        if (ar.succeeded()) {
          S state = ar.result();
          if (addressResolver.isValid(state)) {
            return (Future<S>) ar;
          } else {
            PromiseInternal<S> promise = ctx.promise();
            if (stateRef.compareAndSet(fut, promise.future())) {
              addressResolver.resolve(address).andThen(ar2 -> {
                if (ar2.succeeded()) {
                  AddressEndpoint.this.state.set(ar2.result());
                }
              }).onComplete(promise);
              return promise.future();
            } else {
              return stateRef.get();
            }
          }
        } else {
          return (Future<S>) ar;
        }
      }).compose(state -> addressResolver
        .pickEndpoint(state)
        .compose(endpoint -> {
          incRefCount();
          SocketAddress origin = addressResolver.addressOf(endpoint);
          K apply = zfn.apply(payload, origin);
          Future<C> f = connectionManager.getConnection(ctx, apply, (key, dispose) -> {
            class Disposer implements Runnable {
              @Override
              public void run() {
                addressResolver.removeAddress(state, endpoint);
                decRefCount();
                dispose.run();
              }
            }
            return endpointProvider.create(apply, new Disposer());
          }, timeout);
          return f.map(c -> new ConnectionLookup<>(c, addressResolver, endpoint));
        }));
    }
  }
}
