package io.vertx.test.fakeresolver;

import io.vertx.core.net.SocketAddress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FakeEndpoint {

  final FakeState state;
  final SocketAddress address;
  final List<FakeMetric> metrics = Collections.synchronizedList(new ArrayList<>());

  FakeEndpoint(FakeState state, SocketAddress address) {
    this.state = state;
    this.address = address;
  }

  public List<FakeMetric> metrics() {
    return metrics;
  }
}
