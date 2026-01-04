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

import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.internal.net.endpoint.EndpointResolverInternal;
import io.vertx.core.net.endpoint.*;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.Address;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.internal.resource.ManagedResource;
import io.vertx.core.internal.resource.ResourceManager;
import io.vertx.core.spi.endpoint.EndpointResolver;
import io.vertx.core.spi.endpoint.EndpointBuilder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A resolver for endpoints.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class EndpointResolverImpl<S, A extends Address, N> implements EndpointResolverInternal {

  private final VertxInternal vertx;
  private final LoadBalancer loadBalancer;
  private final EndpointResolver<A, N, S, ListOfServers> endpointResolver;
  private final ResourceManager<A, ManagedEndpoint> endpointManager;
  private final long expirationMillis;

  public EndpointResolverImpl(VertxInternal vertx, EndpointResolver<A, N, S, ?> endpointResolver, LoadBalancer loadBalancer, long expirationMillis) {

    if (loadBalancer == null) {
      loadBalancer = LoadBalancer.ROUND_ROBIN;
    }

    this.vertx = vertx;
    this.loadBalancer = loadBalancer;
    this.endpointResolver = (EndpointResolver<A, N, S, ListOfServers>) endpointResolver;
    this.endpointManager = new ResourceManager<>();
    this.expirationMillis = expirationMillis;
  }

  @Override
  public boolean resolves(Address address) {
    return endpointResolver.tryCast(address) != null;
  }

  /**
   * Trigger the expiration check, this removes unused entries.
   */
  public void checkExpired() {
    endpointManager.checkExpired();
  }

  @Override
  public Future<io.vertx.core.net.endpoint.Endpoint> resolveEndpoint(Address address) {
    return vertx.future(promise -> lookupEndpoint(address, promise));
  }

  public void lookupEndpoint(Address address, Completable<Endpoint> promise) {
    A casted = endpointResolver.tryCast(address);
    if (casted == null) {
      promise.fail("Cannot resolve address " + address);
      return;
    }
    ManagedEndpoint resolved = resolveAddress(casted);
    resolved.endpoint.onComplete(promise);
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
    public List<ServerEndpoint> servers() {
      return endpointResolver.endpoint(state).servers;
    }

    public ServerEndpoint selectServer(Predicate<ServerEndpoint> filter, String key) {
      ListOfServers listOfServers = endpointResolver.endpoint(state);
      EndpointResolverImpl.View view = listOfServers.viewOf(loadBalancer, filter);
      ServerEndpoint selected = view.selectEndpoint(key);
      if (selected != null && !selected.isAvailable()) {
        // Rebuild views
        listOfServers.views.clear();
        view = listOfServers.viewOf(loadBalancer, filter);
        selected = view.selectEndpoint(key);
      }
      return selected;
    }

    private void close() {
      endpointResolver.dispose(state);
    }
  }

  private class ManagedEndpoint extends ManagedResource {

    private final Future<EndpointImpl> endpoint;
    private final AtomicBoolean disposed = new AtomicBoolean();

    public ManagedEndpoint(Future<EndpointImpl> endpoint) {
      super();
      this.endpoint = endpoint;
    }

    @Override
    protected void cleanup() {
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
  private final Function<A, ManagedEndpoint> provider = (key) -> {
    Future<EndpointImpl> holder = resolve(key);
    ManagedEndpoint endpoint = new ManagedEndpoint(holder);
    endpoint.incRefCount();
    return endpoint;
  };

  private final BiFunction<ManagedEndpoint, Boolean, Result> fn = (endpoint, created) -> new Result(endpoint.endpoint, endpoint, created);

  private ManagedEndpoint resolveAddress(A address) {
    Result sFuture = endpointManager.withResource2(address, provider, managedEndpoint -> {
      Future<EndpointImpl> fut = managedEndpoint.endpoint;
      if (fut.succeeded()) {
        EndpointImpl endpoint = fut.result();
        if (!endpointResolver.isValid(endpoint.state)) {
          Future<S> refresh = endpointResolver.refresh(address, endpoint.state);
          if (refresh != null) {
            return new ManagedEndpoint(refresh.map(s -> new EndpointImpl(address, endpoint.lastAccessed, s)));
          } else {
            // todo: should cleanup .... ????
            return null;
          }
        }
      }
      return managedEndpoint;
    }, fn);
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

  private static class View {

    private List<ServerEndpoint> servers;
    private ServerSelector selector;

    View(List<ServerEndpoint> servers, ServerSelector selector) {
      this.servers = servers;
      this.selector = selector;
    }

    private ServerEndpoint selectEndpoint(String routingKey) {
      int idx;
      if (routingKey == null) {
        idx = selector.select();
      } else {
        idx = selector.select(routingKey);
      }
      if (idx >= 0 && idx < servers.size()) {
        return servers.get(idx);
      }
      return null;
    }
  }

  private static class ListOfServers implements Iterable<ServerEndpoint> {

    // Put stuff here I think ...
    final List<ServerEndpoint> servers;
    final Map<Predicate<ServerEndpoint>, View> views;

    private ListOfServers(List<ServerEndpoint> servers) {
      this.servers = servers;
      this.views = new ConcurrentHashMap<>();
    }

    @Override
    public Iterator<ServerEndpoint> iterator() {
      return servers.iterator();
    }

    @Override
    public String toString() {
      return servers.toString();
    }

    View viewOf(LoadBalancer loadBalancer, Predicate<ServerEndpoint> filter) {
      EndpointResolverImpl.View view = views.get(filter);
      if (view == null) {
        List<ServerEndpoint> l = new ArrayList<>(servers.size());
        for (ServerEndpoint s : servers) {
          if (s.isAvailable() && filter.test(s)) {
            l.add(s);
          }
        }
        ServerSelector selector = loadBalancer.selector(l);
        view = new EndpointResolverImpl.View(l,  selector);
        views.put(filter, view);
      }
      return view;
    }
  }

  public class ServerEndpointImpl implements ServerEndpoint {
    final AtomicLong lastAccessed;
    final String key;
    final N endpoint;
    final InteractionMetrics<?> metrics;
    final AtomicInteger connectFailures;
    public ServerEndpointImpl(AtomicLong lastAccessed, String key, N endpoint, InteractionMetrics<?> metrics) {
      this.lastAccessed = lastAccessed;
      this.key = key;
      this.endpoint = endpoint;
      this.metrics = metrics;
      this.connectFailures = new AtomicInteger();
    }
    @Override
    public String key() {
      return key;
    }
    @Override
    public boolean isAvailable() {
      return connectFailures.get() == 0;
    }
    @Override
    public Map<String, String> properties() {
      return endpointResolver.propertiesOf(endpoint);
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
    public ServerInteraction newInteraction() {
      lastAccessed.set(System.currentTimeMillis());
      InteractionMetrics metrics = this.metrics;
      Object metric = metrics.initiateRequest();
      return new ServerInteraction() {
        boolean connected;
        @Override
        public void reportRequestBegin() {
          connected = true;
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
          if (!connected) {
            connectFailures.incrementAndGet();
          }
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
    EndpointBuilder<ListOfServers, N> builder = new EndpointBuilder<>() {

      @Override
      public EndpointBuilder<ListOfServers, N> addServer(N server, String key) {
        List<ServerEndpoint> list = new ArrayList<>();
        InteractionMetrics<?> metrics = loadBalancer.newMetrics();
        list.add(new ServerEndpointImpl(lastAccessed, key, server, metrics));
        return new EndpointBuilder<>() {

          @Override
          public EndpointBuilder<ListOfServers, N> addServer(N server, String key) {
            InteractionMetrics<?> metrics = loadBalancer.newMetrics();
            list.add(new ServerEndpointImpl(lastAccessed, key, server, metrics));
            return this;
          }

          @Override
          public ListOfServers build() {
            return new ListOfServers(list);
          }
        };
      }

      @Override
      public ListOfServers build() {
        return new ListOfServers(Collections.emptyList());
      }
    };

    return endpointResolver
      .resolve(address, builder)
      .map(s -> new EndpointImpl(address, lastAccessed, s));
  }
}
