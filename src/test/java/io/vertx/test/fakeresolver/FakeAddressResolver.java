package io.vertx.test.fakeresolver;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.net.Address;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.loadbalancing.Endpoint;
import io.vertx.core.spi.resolver.address.AddressResolver;
import io.vertx.core.spi.resolver.address.EndpointListBuilder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

public class FakeAddressResolver<B> implements io.vertx.core.net.AddressResolver, AddressResolver<FakeAddress, FakeEndpoint, FakeState<B>, B> {

  class LazyFakeState {
    final String name;
    volatile List<SocketAddress> endpoints;
    AtomicReference<FakeState<B>> state = new AtomicReference<>();
    LazyFakeState(String name) {
      this.name = name;
    }
  }

  private final ConcurrentMap<String, LazyFakeState> map = new ConcurrentHashMap<>();

  public void registerAddress(String name, List<SocketAddress> endpoints) {
    LazyFakeState lazy = map.computeIfAbsent(name, LazyFakeState::new);
    lazy.endpoints = endpoints;
    FakeState prev = lazy.state.getAndSet(null);
    if (prev != null) {
      prev.isValid = false;
    }
  }

  public Set<String> addresses() {
    return map.keySet();
  }

  public List<FakeEndpoint> endpoints(String name) {
    LazyFakeState state = map.get(name);
    if (state != null) {
      Iterator s1 = ((Iterable) state.state.get().endpoints).iterator();
      List<FakeEndpoint> list = new ArrayList<>();
      for (Object o : ((Iterable) state.state.get().endpoints)) {
        list.add((FakeEndpoint) ((Endpoint) o).endpoint());
      }
      return list;
    }
    return null;
  }

  @Override
  public AddressResolver<?, ?, ?, ?> resolver(Vertx vertx) {
    return this;
  }

  @Override
  public FakeAddress tryCast(Address address) {
    return address instanceof FakeAddress ? (FakeAddress) address : null;
  }

  @Override
  public Future<FakeState<B>> resolve(FakeAddress address, EndpointListBuilder<B, FakeEndpoint> builder) {
    LazyFakeState state = map.get(address.name());
    if (state != null) {
      if (state.state.get() == null) {
        for (SocketAddress socketAddress : state.endpoints) {
          builder = builder.addEndpoint(new FakeEndpoint(socketAddress));
        }
        state.state.set(new FakeState<>(state.name, builder.build()));
      }
      return Future.succeededFuture(state.state.get());
    } else {
      return Future.failedFuture("Could not resolve " + address);
    }
  }

  @Override
  public B endpoints(FakeState<B> state) {
    return state.endpoints;
  }

  @Override
  public boolean isValid(FakeState<B> state) {
    return state.isValid;
  }

  @Override
  public SocketAddress addressOfEndpoint(FakeEndpoint endpoint) {
    return endpoint.socketAddress;
  }

  @Override
  public void dispose(FakeState<B> state) {
    map.remove(state.name);
  }

  @Override
  public void close() {
  }
}
