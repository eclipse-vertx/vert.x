package io.vertx.test.fakemetrics;

import io.vertx.core.spi.metrics.TransportMetrics;

public class FakeTCPMetrics extends FakeTransportMetrics implements TransportMetrics<ConnectionMetric> {

  public FakeTCPMetrics(String name) {
    super(name);
  }
}
