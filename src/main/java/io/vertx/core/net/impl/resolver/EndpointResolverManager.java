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
import io.vertx.core.spi.loadbalancing.EndpointMetrics;
import io.vertx.core.loadbalancing.LoadBalancer;
import io.vertx.core.net.Address;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.endpoint.EndpointManager;
import io.vertx.core.net.impl.endpoint.Endpoint;
import io.vertx.core.net.impl.endpoint.EndpointProvider;
import io.vertx.core.spi.loadbalancing.EndpointSelector;
import io.vertx.core.spi.resolver.address.AddressResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

/**
 * A manager for endpoints.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class EndpointResolverManager<S, A extends Address, E> {

  private final LoadBalancer loadBalancer;
  private final AddressResolver<A, E, S> addressResolver;
  private final EndpointManager<A, EndpointImpl> connectionManager;
  private final long expirationMillis;

  public EndpointResolverManager(AddressResolver<A, E, S> addressResolver, LoadBalancer loadBalancer, long expirationMillis) {

    if (loadBalancer == null) {
      loadBalancer = LoadBalancer.ROUND_ROBIN;
    }

    this.loadBalancer = loadBalancer;
    this.addressResolver = addressResolver;
    this.connectionManager = new EndpointManager<>();
    this.expirationMillis = expirationMillis;
  }

  /**
   * @return whether the resolver accepts the {@code address}
   */
  public boolean accepts(Address address) {
    return addressResolver.tryCast(address) != null;
  }

  /**
   * Trigger the expiration check, this removes unused entries.
   */
  public void checkExpired() {
    connectionManager.checkExpired();
  }

  private Future<ManagedState<S>> resolve(A address) {
    EndpointSelector selector = loadBalancer.selector();
    return addressResolver.resolve(e -> new ManagedEndpoint<>(e, selector.endpointMetrics()), address)
      .map(s -> new ManagedState<>(selector, s));
  }

  /**
   * Select an endpoint.
   *
   * @param state the state
   * @return the resolved endpoint
   */
  private ManagedEndpoint<E> selectEndpoint(ManagedState<S> state) {

    List<io.vertx.core.spi.resolver.address.Endpoint<E>> lst = addressResolver.endpoints(state.state);
    List<EndpointMetrics<?>> other = new ArrayList<>();
    // TRY AVOID THAT
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
        // max 4
        if (attempts < 4) {
          return lookupEndpoint(ctx, address, attempts + 1);
        } else {
          return ctx.failedFuture("Too many attempts");
        }
      }
      ManagedEndpoint<E> endpoint = selectEndpoint(state);
      return ctx.succeededFuture(new EndpointLookup() {
        @Override
        public SocketAddress address() {
          return addressResolver.addressOfEndpoint(endpoint.value);
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

  private class EndpointImpl extends Endpoint {

    private volatile Future<ManagedState<S>> fut;
    private final AtomicLong lastAccessed;
    private final AtomicBoolean disposed = new AtomicBoolean();

    public EndpointImpl(Future<ManagedState<S>> fut, Runnable dispose) {
      super(dispose);
      this.fut = fut;
      this.lastAccessed = new AtomicLong(System.currentTimeMillis());
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
    EndpointProvider<A, EndpointImpl> provider = (key, dispose) -> {
      Future<ManagedState<S>> fut = resolve(key);
      EndpointImpl endpoint = new EndpointImpl(fut, dispose);
      endpoint.incRefCount();
      return endpoint;
    };
    BiFunction<EndpointImpl, Boolean, Result> fn = (endpoint, created) -> {
      if (refresh) {
        endpoint.fut = resolve(address);
      }
      return new Result(endpoint.fut, endpoint, created);
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
