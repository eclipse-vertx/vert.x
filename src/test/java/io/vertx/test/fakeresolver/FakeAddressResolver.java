package io.vertx.test.fakeresolver;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.net.Address;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.resolver.address.AddressResolver;
import io.vertx.core.spi.resolver.address.Endpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FakeAddressResolver implements io.vertx.core.net.AddressResolver, AddressResolver<FakeAddress, FakeEndpoint, FakeState> {

  static class LazyFakeState {
    final String name;
    volatile List<SocketAddress> endpoints;
    AtomicReference<FakeState> state = new AtomicReference<>();
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
      return state.state.get().endpoints.stream().map(Endpoint::get).collect(Collectors.toList());
    }
    return null;
  }

  @Override
  public AddressResolver<?, ?, ?> resolver(Vertx vertx) {
    return this;
  }

  @Override
  public FakeAddress tryCast(Address address) {
    return address instanceof FakeAddress ? (FakeAddress) address : null;
  }

  @Override
  public Future<FakeState> resolve(Function<FakeEndpoint, Endpoint<FakeEndpoint>> factory, FakeAddress address) {
    LazyFakeState state = map.get(address.name());
    if (state != null) {
      if (state.state.get() == null) {
        List<Endpoint<FakeEndpoint>> lst = new ArrayList<>();
        for (SocketAddress socketAddress : state.endpoints) {
          lst.add(factory.apply(new FakeEndpoint(socketAddress)));
        }
        state.state.set(new FakeState(state.name, lst));
      }
      return Future.succeededFuture(state.state.get());
    } else {
      return Future.failedFuture("Could not resolve " + address);
    }
  }

  @Override
  public List<Endpoint<FakeEndpoint>> endpoints(FakeState state) {
    return state.endpoints;
  }

  @Override
  public boolean isValid(FakeState state) {
    return state.isValid;
  }

  @Override
  public SocketAddress addressOfEndpoint(FakeEndpoint endpoint) {
    return endpoint.socketAddress;
  }

  @Override
  public void dispose(FakeState state) {
    map.remove(state.name);
  }

  @Override
  public void close() {
  }
}
