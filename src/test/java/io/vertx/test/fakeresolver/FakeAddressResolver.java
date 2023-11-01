package io.vertx.test.fakeresolver;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.net.Address;
import io.vertx.core.net.AddressResolver;
import io.vertx.core.net.SocketAddress;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class FakeAddressResolver implements AddressResolver, io.vertx.core.spi.net.AddressResolver<FakeState, FakeAddress, FakeMetric, FakeEndpoint> {

  private final ConcurrentMap<String, FakeState> map = new ConcurrentHashMap<>();

  public void registerAddress(String name, List<SocketAddress> endpoints) {
    FakeState prev = map.put(name, new FakeState(name, endpoints));
    if (prev != null) {
      prev.valid = false;
    }
  }

  public Set<String> addresses() {
    return map.keySet();
  }

  public List<FakeEndpoint> endpoints(String name) {
    FakeState state = map.get(name);
    return state != null ? state.endpoints : null;
  }

  @Override
  public io.vertx.core.spi.net.AddressResolver<?, ?, ?, ?> resolver(Vertx vertx) {
    return this;
  }

  @Override
  public boolean isValid(FakeState state) {
    return state.valid;
  }


  @Override
  public FakeMetric initiateRequest(FakeEndpoint endpoint) {
    FakeMetric metric = new FakeMetric(endpoint);
    endpoint.metrics.add(metric);
    return metric;
  }

  @Override
  public void requestBegin(FakeMetric metric) {
    metric.requestBegin = System.currentTimeMillis();
  }

  @Override
  public SocketAddress addressOf(FakeEndpoint endpoint) {
    return endpoint.socketAddress;
  }

  @Override
  public void requestEnd(FakeMetric metric) {
    metric.requestEnd = System.currentTimeMillis();
  }

  @Override
  public void responseBegin(FakeMetric metric) {
    metric.responseBegin = System.currentTimeMillis();
  }

  @Override
  public void responseEnd(FakeMetric metric) {
    metric.responseEnd = System.currentTimeMillis();
  }

  @Override
  public void requestFailed(FakeMetric metric, Throwable failure) {
    metric.failure = failure;
  }

  @Override
  public FakeAddress tryCast(Address address) {
    return address instanceof FakeAddress ? (FakeAddress) address : null;
  }

  @Override
  public Future<FakeState> resolve(FakeAddress address) {
    FakeState state = map.get(address.name());
    if (state != null) {
      return Future.succeededFuture(state);
    } else {
      return Future.failedFuture("Could not resolve " + address);
    }
  }

  @Override
  public FakeEndpoint pickEndpoint(FakeState state) {
    int idx = state.index++;
    FakeEndpoint result = state.endpoints.get(idx % state.endpoints.size());
    return result;
  }

  @Override
  public void removeAddress(FakeState state, FakeEndpoint endpoint) {
    state.endpoints.remove(endpoint);
  }

  @Override
  public void dispose(FakeState state) {
    map.remove(state.name);
  }
}
