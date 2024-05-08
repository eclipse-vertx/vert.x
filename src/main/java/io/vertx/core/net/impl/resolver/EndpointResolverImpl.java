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
import io.vertx.core.spi.resolver.address.EndpointListBuilder;
import io.vertx.core.spi.resolver.endpoint.EndpointLookup;
import io.vertx.core.spi.resolver.endpoint.EndpointRequest;
import io.vertx.core.spi.resolver.endpoint.EndpointResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * A manager for endpoints.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class EndpointResolverImpl<S, A extends Address, E> implements EndpointResolver<A> {

  private final LoadBalancer loadBalancer;
  private final AddressResolver<A, E, S, ListOfEndpoints> addressResolver;
  private final EndpointManager<A, EndpointImpl> endpointManager;
  private final long expirationMillis;

  public EndpointResolverImpl(AddressResolver<A, E, S, ?> addressResolver, LoadBalancer loadBalancer, long expirationMillis) {

    if (loadBalancer == null) {
      loadBalancer = LoadBalancer.ROUND_ROBIN;
    }

    this.loadBalancer = loadBalancer;
    this.addressResolver = (AddressResolver<A, E, S, ListOfEndpoints>) addressResolver;
    this.endpointManager = new EndpointManager<>();
    this.expirationMillis = expirationMillis;
  }

  /**
   * @return whether the resolver accepts the {@code address}
   */
  public A accepts(Address address) {
    return addressResolver.tryCast(address);
  }

  /**
   * Trigger the expiration check, this removes unused entries.
   */
  public void checkExpired() {
    endpointManager.checkExpired();
  }

  /**
   * Select an endpoint.
   *
   * @param state the state
   * @return the resolved endpoint
   */
  private io.vertx.core.spi.loadbalancing.Endpoint<E> selectEndpoint(S state, String routingKey) {
    ListOfEndpoints listOfEndpoints = addressResolver.endpoints(state);
    int idx;
    if (routingKey == null) {
      idx = listOfEndpoints.selector.selectEndpoint();
    } else {
      idx = listOfEndpoints.selector.selectEndpoint(routingKey);
    }
    if (idx >= 0 && idx < listOfEndpoints.list.size()) {
      return listOfEndpoints.list.get(idx);
    }
    return null;
  }

  /**
   * Perform an endpoint lookup for the given {@code address}
   *
   * @param ctx the context
   * @param address the address to lookup
   * @return a future notified with the lookup
   */
  public Future<EndpointLookup> lookupEndpoint(ContextInternal ctx, Address address) {
    return lookupEndpoint(ctx, address, 0, "");
  }

  @Override
  public Future<EndpointLookup> lookupEndpoint(ContextInternal ctx, A address, String routingKey) {
    return lookupEndpoint(ctx, address, 0, routingKey);
  }

  private Future<EndpointLookup> lookupEndpoint(ContextInternal ctx, Address address, int attempts, String routingKey) {
    A casted = addressResolver.tryCast(address);
    if (casted == null) {
      return ctx.failedFuture("Cannot resolve address " + address);
    }
    EndpointImpl resolved = resolveAddress(casted, attempts > 0);
    return resolved.fut.compose(state -> {
      if (!addressResolver.isValid(state)) {
        // max 4
        if (attempts < 4) {
          return lookupEndpoint(ctx, address, attempts + 1, routingKey);
        } else {
          return ctx.failedFuture("Too many attempts");
        }
      }
      io.vertx.core.spi.loadbalancing.Endpoint<E> endpoint = selectEndpoint(state, routingKey);
      if (endpoint == null) {
        return ctx.failedFuture("No results");
      }
      return ctx.succeededFuture(new EndpointLookup() {
        @Override
        public SocketAddress address() {
          return addressResolver.addressOfEndpoint(endpoint.endpoint());
        }
        @Override
        public EndpointRequest initiateRequest() {
          resolved.lastAccessed.set(System.currentTimeMillis());
          EndpointMetrics metrics = endpoint.metrics();
          Object metric = metrics.initiateRequest();
          return new EndpointRequest() {
            @Override
            public void reportRequestBegin() {
              metrics.reportRequestBegin(metric);
            }
            @Override
            public void reportRequestEnd() {
              metrics.reportRequestEnd(metric);
            }
            @Override
            public void reportResponseBegin() {
              metrics.reportResponseBegin(metric);
            }
            @Override
            public void reportResponseEnd() {
              metrics.reportResponseEnd(metric);
            }
            @Override
            public void reportFailure(Throwable failure) {
              metrics.reportFailure(metric, failure);
            }
          };
        }
      });
    });
  }

  private class EndpointImpl extends Endpoint {

    private volatile Future<S> fut;
    private final AtomicLong lastAccessed;
    private final AtomicBoolean disposed = new AtomicBoolean();

    public EndpointImpl(Future<S> fut, Runnable dispose) {
      super(dispose);
      this.fut = fut;
      this.lastAccessed = new AtomicLong(System.currentTimeMillis());
    }

    @Override
    protected void dispose() {
      if (fut.succeeded()) {
        addressResolver.dispose(fut.result());
      }
    }

    @Override
    protected void checkExpired() {
//      Future<S> f = fut;
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
    final Future<S> fut;
    final EndpointImpl endpoint;
    final boolean created;
    public Result(Future<S> fut, EndpointImpl endpoint, boolean created) {
      this.fut = fut;
      this.endpoint = endpoint;
      this.created = created;
    }
  }

  // Does not depend on address
  private final EndpointProvider<A, EndpointImpl> provider = (key, dispose) -> {
    Future<S> fut = resolve(key);
    EndpointImpl endpoint = new EndpointImpl(fut, dispose);
    endpoint.incRefCount();
    return endpoint;
  };

  private final BiFunction<EndpointImpl, Boolean, Result> fn = (endpoint, created) -> new Result(endpoint.fut, endpoint, created);

  private EndpointImpl resolveAddress(A address, boolean refresh) {
    Predicate<EndpointImpl> checker;
    if (refresh) {
      checker = t -> false;
    } else {
      checker = t -> true;
    }
    Result sFuture = endpointManager.withEndpoint2(address, provider, checker, fn);
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

  class ListOfEndpoints implements Iterable {
    final List<io.vertx.core.spi.loadbalancing.Endpoint<E>> list;
    final EndpointSelector selector;
    private ListOfEndpoints(List<io.vertx.core.spi.loadbalancing.Endpoint<E>> list, EndpointSelector selector) {
      this.list = list;
      this.selector = selector;
    }
    @Override
    public Iterator iterator() {
      return list.iterator();
    }
  }

  private Future<S> resolve(A address) {
    EndpointListBuilder<ListOfEndpoints, E> builder = new EndpointListBuilder<>() {
      @Override
      public EndpointListBuilder<ListOfEndpoints, E> addEndpoint(E endpoint, String key) {
        List<io.vertx.core.spi.loadbalancing.Endpoint<E>> list = new ArrayList<>();
        io.vertx.core.spi.loadbalancing.Endpoint<E> e = loadBalancer.endpointOf(endpoint, key);
        list.add(e);
        return new EndpointListBuilder<>() {
          @Override
          public EndpointListBuilder<ListOfEndpoints, E> addEndpoint(E endpoint, String key) {
            io.vertx.core.spi.loadbalancing.Endpoint<E> e = loadBalancer.endpointOf(endpoint, key);
            list.add(e);
            return this;
          }
          @Override
          public ListOfEndpoints build() {
            return new ListOfEndpoints(list, loadBalancer.selector(list));
          }
        };
      }
      @Override
      public ListOfEndpoints build() {
        return new ListOfEndpoints(Collections.emptyList(), () -> -1); // Make this immutable and shared
      }
    };
    return addressResolver.resolve(address, builder);
  }
}
