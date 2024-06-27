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
package io.vertx.core.net.endpoint.impl;

import io.vertx.core.Future;
import io.vertx.core.internal.net.endpoint.EndpointResolverInternal;
import io.vertx.core.net.endpoint.EndpointNode;
import io.vertx.core.net.endpoint.EndpointInteraction;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.endpoint.InteractionMetrics;
import io.vertx.core.net.endpoint.LoadBalancer;
import io.vertx.core.net.Address;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.endpoint.Endpoint;
import io.vertx.core.net.impl.endpoint.EndpointProvider;
import io.vertx.core.net.endpoint.EndpointSelector;
import io.vertx.core.spi.endpoint.EndpointResolver;
import io.vertx.core.spi.endpoint.EndpointBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

/**
 * A manager for endpoints.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class EndpointResolverImpl<S, A extends Address, E> implements EndpointResolverInternal {

  private final VertxInternal vertx;
  private final LoadBalancer loadBalancer;
  private final EndpointResolver<A, E, S, ListOfNodes> endpointResolver;
  private final io.vertx.core.net.impl.endpoint.EndpointManager<A, ManagedEndpoint> endpointManager;
  private final long expirationMillis;

  public EndpointResolverImpl(VertxInternal vertx, EndpointResolver<A, E, S, ?> endpointResolver, LoadBalancer loadBalancer, long expirationMillis) {

    if (loadBalancer == null) {
      loadBalancer = LoadBalancer.ROUND_ROBIN;
    }

    this.vertx = vertx;
    this.loadBalancer = loadBalancer;
    this.endpointResolver = (EndpointResolver<A, E, S, ListOfNodes>) endpointResolver;
    this.endpointManager = new io.vertx.core.net.impl.endpoint.EndpointManager<>();
    this.expirationMillis = expirationMillis;
  }

  /**
   * Trigger the expiration check, this removes unused entries.
   */
  public void checkExpired() {
    endpointManager.checkExpired();
  }

  @Override
  public Future<io.vertx.core.net.endpoint.Endpoint> resolveEndpoint(Address address) {
    return lookupEndpoint2(vertx.getOrCreateContext(), address);
  }

  public Future<io.vertx.core.net.endpoint.Endpoint> lookupEndpoint(ContextInternal ctx, Address address) {
    return lookupEndpoint2(ctx, address);
  }

  private class EndpointImpl implements io.vertx.core.net.endpoint.Endpoint {
    private final AtomicLong lastAccessed;
    private final A address;
    private final S state;
    public EndpointImpl(A address, AtomicLong lastAccessed, S state) {
      this.state = state;
      this.address = address;
      this.lastAccessed = lastAccessed;
    }
    @Override
    public List<EndpointNode> nodes() {
      return endpointResolver.endpoint(state).nodes;
    }
    public void close() {
      endpointResolver.dispose(state);
    }
    private EndpointNode selectEndpoint(S state, String routingKey) {
      ListOfNodes listOfNodes = endpointResolver.endpoint(state);
      int idx;
      if (routingKey == null) {
        idx = listOfNodes.selector.select();
      } else {
        idx = listOfNodes.selector.select(routingKey);
      }
      if (idx >= 0 && idx < listOfNodes.nodes.size()) {
        return listOfNodes.nodes.get(idx);
      }
      return null;
    }
    public EndpointNode selectNode(String key) {
      if (!endpointResolver.isValid(state)) {
        throw new IllegalStateException("Cannot resolve address " + address );
      }
      EndpointNode endpoint = selectEndpoint(state, key);
      if (endpoint == null) {
        throw new IllegalStateException("No results for " + address );
      }
      return endpoint;
    }
  }

  private Future<io.vertx.core.net.endpoint.Endpoint> lookupEndpoint2(ContextInternal ctx, Address address) {
    A casted = endpointResolver.tryCast(address);
    if (casted == null) {
      return ctx.failedFuture("Cannot resolve address " + address);
    }
    ManagedEndpoint resolved = resolveAddress(casted);
    return (Future) resolved.endpoint;
  }

  private class ManagedEndpoint extends Endpoint {

    private final Future<EndpointImpl> endpoint;
    private final AtomicBoolean disposed = new AtomicBoolean();

    public ManagedEndpoint(Future<EndpointImpl> endpoint, Runnable dispose) {
      super(dispose);
      this.endpoint = endpoint;
    }

    @Override
    protected void dispose() {
      if (endpoint.succeeded()) {
        endpoint.result().close();
      }
    }

    @Override
    protected void checkExpired() {
      if (endpoint.succeeded() && expirationMillis > 0 && System.currentTimeMillis() - endpoint.result().lastAccessed.get() >= expirationMillis) {
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
    final Future<EndpointImpl> fut;
    final ManagedEndpoint endpoint;
    final boolean created;
    public Result(Future<EndpointImpl> fut, ManagedEndpoint endpoint, boolean created) {
      this.fut = fut;
      this.endpoint = endpoint;
      this.created = created;
    }
  }

  // Does not depend on address
  private final EndpointProvider<A, ManagedEndpoint> provider = (key, dispose) -> {
    Future<EndpointImpl> holder = resolve(key);
    ManagedEndpoint endpoint = new ManagedEndpoint(holder, dispose);
    endpoint.incRefCount();
    return endpoint;
  };

  private final BiFunction<ManagedEndpoint, Boolean, Result> fn = (endpoint, created) -> new Result(endpoint.endpoint, endpoint, created);

  private ManagedEndpoint resolveAddress(A address) {
    Result sFuture = endpointManager.withEndpoint2(address, provider, t -> true, fn);
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

  private static class ListOfNodes implements Iterable<EndpointNode> {
    final List<EndpointNode> nodes;
    final EndpointSelector selector;
    private ListOfNodes(List<EndpointNode> nodes, EndpointSelector selector) {
      this.nodes = nodes;
      this.selector = selector;
    }
    @Override
    public Iterator<EndpointNode> iterator() {
      return nodes.iterator();
    }
    @Override
    public String toString() {
      return nodes.toString();
    }
  }

  public class EndpointNodeImpl implements EndpointNode {
    final AtomicLong lastAccessed;
    final String key;
    final E endpoint;
    final InteractionMetrics<?> metrics;
    public EndpointNodeImpl(AtomicLong lastAccessed, String key, E endpoint, InteractionMetrics<?> metrics) {
      this.lastAccessed = lastAccessed;
      this.key = key;
      this.endpoint = endpoint;
      this.metrics = metrics;
    }
    @Override
    public String key() {
      return key;
    }
    @Override
    public Object unwrap() {
      return endpoint;
    }
    @Override
    public InteractionMetrics<?> metrics() {
      return metrics;
    }
    @Override
    public SocketAddress address() {
      return endpointResolver.addressOf(endpoint);
    }
    @Override
    public EndpointInteraction newInteraction() {
      lastAccessed.set(System.currentTimeMillis());
      InteractionMetrics metrics = this.metrics;
      Object metric = metrics.initiateRequest();
      return new EndpointInteraction() {
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
    @Override
    public String toString() {
      return String.valueOf(endpoint);
    }
  }

  private Future<EndpointImpl> resolve(A address) {
    AtomicLong lastAccessed = new AtomicLong(System.currentTimeMillis());
    EndpointBuilder<ListOfNodes, E> builder = new EndpointBuilder<>() {
      @Override
      public EndpointBuilder<ListOfNodes, E> addNode(E node, String key) {
        List<EndpointNode> list = new ArrayList<>();
        InteractionMetrics<?> metrics = loadBalancer.newMetrics();
        list.add(new EndpointNodeImpl(lastAccessed, key, node, metrics));
        return new EndpointBuilder<>() {
          @Override
          public EndpointBuilder<ListOfNodes, E> addNode(E node, String key) {
            InteractionMetrics<?> metrics = loadBalancer.newMetrics();
            list.add(new EndpointNodeImpl(lastAccessed, key, node, metrics));
            return this;
          }
          @Override
          public ListOfNodes build() {
            return new ListOfNodes(list, loadBalancer.selector(list));
          }
        };
      }
      @Override
      public ListOfNodes build() {
        return new ListOfNodes(Collections.emptyList(), () -> -1);
      }
    };
    return endpointResolver
      .resolve(address, builder)
      .map(s -> new EndpointImpl(address, lastAccessed, s));
  }
}
