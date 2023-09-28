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
import io.vertx.core.spi.lookup.AddressResolver;

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
public class EndpointResolver<S, K, C, A extends Address> {

  private final AddressResolver<S, A, ?> addressResolver;
  private final ConnectionManager<A, C> connectionManager;

  public EndpointResolver(AddressResolver<S, A, ?> addressResolver) {
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
                            Function<Endpoint<C>, Optional<T>> fn) {
    A resolverAddress = addressResolver.tryCast(address);
    if (resolverAddress == null) {
      return null;
    } else {
      EndpointProvider<A, C> provider = (key, disposer) -> new ResolvedEndpoint(
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

  public class ResolvedEndpoint<P> extends Endpoint<C> {

    private final AtomicReference<S> state;
    private final AtomicReference<Future<S>> stateRef;
    private final ConnectionManager<EndpointKeyWrapper, C> connectionManager;
    private final A address;
    private final EndpointProvider<K, C> endpointProvider;
    private final BiFunction<P, SocketAddress, K> zfn;

    public ResolvedEndpoint(Future<S> stateRef, Runnable disposer, A address, EndpointProvider<K, C> endpointProvider, BiFunction<P, SocketAddress, K> zfn) {
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

    public AddressResolver<S, A, ?> addressResolver() {
      return addressResolver;
    }

    @Override
    public void checkExpired() {
      connectionManager.checkExpired();
    }

    @Override
    public Future<C> requestConnection(ContextInternal ctx, long timeout) {
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
                  ResolvedEndpoint.this.state.set(ar2.result());
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
        .pickAddress(state)
        .compose(origin -> {
          incRefCount();
          K apply = zfn.apply(payload, origin);
          return connectionManager.getConnection(ctx, new EndpointKeyWrapper(apply) {
            @Override
            void cleanup() {
              addressResolver.removeAddress(state, origin);
              decRefCount();
            }
          }, (key, dispose) -> {
            class Disposer implements Runnable {
              @Override
              public void run() {
                key.cleanup();
                dispose.run();
              }
            }
            return endpointProvider.create(apply, new Disposer());
          }, timeout);
        }));
    }
  }

  private abstract class EndpointKeyWrapper {
    final K address;
    public EndpointKeyWrapper(K address) {
      this.address = address;
    }
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
      if (obj instanceof EndpointResolver.EndpointKeyWrapper) {
        EndpointKeyWrapper that = (EndpointKeyWrapper) obj;
        return address.equals(that.address);
      }
      return false;
    }
    @Override
    public String toString() {
      return "EndpointKey(z=" + address + ")";
    }
  }
}
