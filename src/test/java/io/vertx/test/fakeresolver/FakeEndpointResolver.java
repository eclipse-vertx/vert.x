package io.vertx.test.fakeresolver;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.net.Address;
import io.vertx.core.net.AddressResolver;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.endpoint.EndpointNode;
import io.vertx.core.spi.endpoint.EndpointResolver;
import io.vertx.core.spi.endpoint.EndpointBuilder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

public class FakeEndpointResolver<B> implements AddressResolver, EndpointResolver<FakeAddress, FakeEndpoint, FakeState<B>, B> {

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
        EndpointNode instance = (EndpointNode) o;
        list.add((FakeEndpoint) instance.unwrap());
      }
      return list;
    }
    return null;
  }

  @Override
  public EndpointResolver<?, ?, ?, ?> endpointResolver(Vertx vertx) {
    return this;
  }

  @Override
  public FakeAddress tryCast(Address address) {
    return address instanceof FakeAddress ? (FakeAddress) address : null;
  }

  @Override
  public Future<FakeState<B>> resolve(FakeAddress address, EndpointBuilder<B, FakeEndpoint> builder) {
    LazyFakeState state = map.get(address.name());
    if (state != null) {
      if (state.state.get() == null) {
        for (SocketAddress socketAddress : state.endpoints) {
          builder = builder.addNode(new FakeEndpoint(socketAddress));
        }
        state.state.set(new FakeState<>(state.name, builder.build()));
      }
      return Future.succeededFuture(state.state.get());
    } else {
      return Future.failedFuture("Could not resolve " + address);
    }
  }

  @Override
  public B endpoint(FakeState<B> data) {
    return data.endpoints;
  }

  @Override
  public boolean isValid(FakeState<B> data) {
    return data.isValid;
  }

  @Override
  public SocketAddress addressOf(FakeEndpoint server) {
    return server.socketAddress;
  }

  @Override
  public void dispose(FakeState<B> data) {
    map.remove(data.name);
  }

  @Override
  public void close() {
  }
}
