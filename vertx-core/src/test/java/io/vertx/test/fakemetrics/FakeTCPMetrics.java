package io.vertx.test.fakemetrics;

import io.vertx.core.spi.metrics.TCPMetrics;

public class FakeTCPMetrics extends FakeTransportMetrics implements TCPMetrics<SocketMetric> {

  public FakeTCPMetrics(String name) {
    super(name);
  }
}
