package io.vertx.test.fakeresolver;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.net.Address;
import io.vertx.core.net.AddressResolver;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.endpoint.EndpointResolver;
import io.vertx.core.spi.endpoint.EndpointBuilder;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class FakeAddressResolver<B> implements AddressResolver<FakeAddress>, EndpointResolver<FakeAddress, FakeServerEndpoint, FakeState<B>, B> {

  private final ConcurrentMap<String, FakeRegistration> map = new ConcurrentHashMap<>();

  public FakeRegistration registerAddress(String name, List<SocketAddress> servers) {
    FakeRegistration registration = new FakeRegistration(servers);
    map.put(name, registration);
    return registration;
  }

  public Set<String> addresses() {
    return map.keySet();
  }

  @Override
  public EndpointResolver<FakeAddress, ?, ?, ?> endpointResolver(Vertx vertx) {
    return this;
  }

  @Override
  public FakeAddress tryCast(Address address) {
    return address instanceof FakeAddress ? (FakeAddress) address : null;
  }

  @Override
  public Future<FakeState<B>> resolve(FakeAddress address, EndpointBuilder<B, FakeServerEndpoint> builder) {
    FakeRegistration registration = map.get(address.name());
    if (registration != null) {
      FakeState<B> state = (FakeState<B>) registration.state;
      if (state == null) {
        state = new FakeState<>(address.name(), registration, builder);
        state.refresh();
        registration.state = state;
      }
      return Future.succeededFuture(state);
    } else {
      return Future.failedFuture("Could not resolve " + address);
    }
  }

  @Override
  public B endpoint(FakeState<B> state) {
    return state.endpoints;
  }

  @Override
  public boolean isValid(FakeState<B> state) {
    FakeRegistration registration = map.get(state.name);
    return state.registration == registration;
  }

  @Override
  public SocketAddress addressOf(FakeServerEndpoint server) {
    return server.socketAddress;
  }

  @Override
  public void dispose(FakeState<B> data) {
    data.registration.state = null;
  }

  @Override
  public void close() {
  }
}
