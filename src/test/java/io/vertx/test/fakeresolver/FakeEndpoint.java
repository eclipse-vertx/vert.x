package io.vertx.test.fakeresolver;

import io.vertx.core.net.SocketAddress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FakeEndpoint {

  final FakeState state;
  final SocketAddress socketAddress;
  final List<FakeMetric> metrics = Collections.synchronizedList(new ArrayList<>());

  FakeEndpoint(FakeState state, SocketAddress socketAddress) {
    this.state = state;
    this.socketAddress = socketAddress;
  }

  public SocketAddress socketAddress() {
    return socketAddress;
  }

  public List<FakeMetric> metrics() {
    return metrics;
  }
}
