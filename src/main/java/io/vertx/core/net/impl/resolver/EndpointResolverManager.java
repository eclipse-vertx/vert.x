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
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.loadbalancing.EndpointMetrics;
import io.vertx.core.loadbalancing.LoadBalancer;
import io.vertx.core.net.Address;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.pool.ConnectionManager;
import io.vertx.core.net.impl.pool.Endpoint;
import io.vertx.core.net.impl.pool.EndpointProvider;
import io.vertx.core.spi.net.AddressResolver;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

/**
 * A manager for endpoints.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class EndpointResolverManager<S, A extends Address, E> {

  private final ResolverImpl<A, E, S> addressResolver;
  private final ConnectionManager<A, ManagedState<S>> connectionManager;
  private final long expirationMillis;

  public EndpointResolverManager(AddressResolver<A, E, S> addressResolver, LoadBalancer loadBalancer, long expirationMillis) {
    this.addressResolver = new ResolverImpl<>(loadBalancer, addressResolver);
    this.connectionManager = new ConnectionManager<>();
    this.expirationMillis = expirationMillis;
  }

  /**
   * Trigger the expiration check, this removes unused entries.
   */
  public void checkExpired() {
    connectionManager.checkExpired();
  }

  /**
   * Perform an endpoint lookup for the given {@code address}
   *
   * @param ctx the context
   * @param address the address to lookup
   * @return a future notified with the lookup
   */
  public Future<EndpointLookup> lookupEndpoint(ContextInternal ctx, Address address) {
    return lookupEndpoint(ctx, address, 0);
  }

  private Future<EndpointLookup> lookupEndpoint(ContextInternal ctx, Address address, int attempts) {
    A casted = addressResolver.tryCast(address);
    if (casted == null) {
      return ctx.failedFuture("Cannot resolve address " + address);
    }
    EndpointImpl ei = resolveAddress(ctx, casted, attempts > 0);
    return ei.fut.compose(state -> {
      if (!addressResolver.isValid(state.state)) {
        if (attempts < 4) {
          return lookupEndpoint(ctx, address, attempts + 1);
        } else {
          return ctx.failedFuture("Too many attempts");
        }
      }
      ManagedEndpoint<E> endpoint = addressResolver.pickEndpoint(state);
      return ctx.succeededFuture(new EndpointLookup() {
        @Override
        public SocketAddress address() {
          return addressResolver.addressOf(endpoint);
        }
        @Override
        public EndpointRequest initiateRequest() {
          ei.lastAccessed.set(System.currentTimeMillis());
          EndpointMetrics abc = endpoint.endpoint;
          Object metric = abc.initiateRequest();
          return new EndpointRequest() {
            @Override
            public void reportRequestBegin() {
              abc.reportRequestBegin(metric);
            }
            @Override
            public void reportRequestEnd() {
              abc.reportRequestEnd(metric);
            }
            @Override
            public void reportResponseBegin() {
              abc.reportResponseBegin(metric);
            }
            @Override
            public void reportResponseEnd() {
              abc.reportResponseEnd(metric);
            }
            @Override
            public void reportFailure(Throwable failure) {
              abc.reportFailure(metric, failure);
            }
          };
        }
      });
    });
  }

  private class EndpointImpl extends Endpoint<ManagedState<S>> {

    private volatile Future<ManagedState<S>> fut;
    private final AtomicLong lastAccessed;
    private final AtomicBoolean disposed = new AtomicBoolean();

    public EndpointImpl(Future<ManagedState<S>> fut, Runnable dispose) {
      super(dispose);
      this.fut = fut;
      this.lastAccessed = new AtomicLong(System.currentTimeMillis());
    }

    @Override
    public Future<ManagedState<S>> requestConnection(ContextInternal ctx, long timeout) {
      return fut;
    }

    @Override
    protected void dispose() {
      if (fut.succeeded()) {
        addressResolver.dispose(fut.result().state);
      }
    }

    @Override
    protected void checkExpired() {
//      Future<ManagedState<S>> f = fut;
      if (/*(f.succeeded() && !addressResolver.isValid(f.result().state)) ||*/ expirationMillis > 0 && System.currentTimeMillis() - lastAccessed.get() >= expirationMillis) {
        if (disposed.compareAndSet(false, true)) {
          decRefCount();
        }
      }
    }

    @Override
    public boolean incRefCount() {
      return super.incRefCount();
    }

    @Override
    public boolean decRefCount() {
      return super.decRefCount();
    }
  }

  /**
   * Internal structure.
   */
  private class Result {
    final Future<ManagedState<S>> fut;
    final EndpointImpl endpoint;
    final boolean created;
    public Result(Future<ManagedState<S>> fut, EndpointImpl endpoint, boolean created) {
      this.fut = fut;
      this.endpoint = endpoint;
      this.created = created;
    }
  }

  private EndpointImpl resolveAddress(ContextInternal ctx, A address, boolean refresh) {
    EndpointProvider<A, ManagedState<S>> provider = (key, dispose) -> {
      Future<ManagedState<S>> fut = addressResolver.resolve(key);
      EndpointImpl endpoint = new EndpointImpl(fut, dispose);
      endpoint.incRefCount();
      return endpoint;
    };
    BiFunction<Endpoint<ManagedState<S>>, Boolean, Optional<Result>> fn = (endpoint, created) -> {
      if (refresh) {
        ((EndpointImpl) endpoint).fut = addressResolver.resolve(address);
      }
      return Optional.of(new Result(endpoint.getConnection(ctx, 0), (EndpointImpl) endpoint, created));
    };
    Result sFuture = connectionManager.withEndpoint(address, provider, fn);
    if (sFuture.created) {
      sFuture.fut.onFailure(err -> {
        if (sFuture.endpoint.disposed.compareAndSet(false, true)) {
          // We need to call decRefCount outside the withEndpoint method, hence we need
          // the Result class workaround
          sFuture.endpoint.decRefCount();
        }
      });
    }
    return sFuture.endpoint;
  }
}
