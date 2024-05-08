package io.vertx.test.fakeloadbalancer;

import io.vertx.core.spi.loadbalancing.Endpoint;
import io.vertx.core.spi.loadbalancing.EndpointSelector;
import io.vertx.core.loadbalancing.LoadBalancer;
import io.vertx.core.spi.loadbalancing.EndpointMetrics;

import java.util.ArrayList;
import java.util.List;

public class FakeLoadBalancer implements LoadBalancer {

  List<? extends Endpoint<?>> endpoints;

  public List<? extends Endpoint<?>> endpoints() {
    return endpoints;
  }

  @Override
  public EndpointSelector selector(List<? extends Endpoint<?>> endpoints) {
    this.endpoints = endpoints;
    return LoadBalancer.ROUND_ROBIN.selector(endpoints);
  }

  @Override
  public <E> Endpoint<E> endpointOf(E endpoint, String id) {
    return new FakeEndpointMetrics<>(endpoint, id);
  }

  public static class FakeEndpointMetrics<E> implements EndpointMetrics<FakeMetric>, Endpoint<E> {

    List<FakeMetric> metrics = new ArrayList<>();

    public List<FakeMetric> metrics2() {
      return metrics;
    }

    private final E endpoint;
    private final String id;

    public FakeEndpointMetrics(E endpoint, String id) {
      this.endpoint = endpoint;
      this.id = id;
    }

    @Override
    public EndpointMetrics<?> metrics() {
      return this;
    }

    @Override
    public String key() {
      return id;
    }

    @Override
    public E endpoint() {
      return endpoint;
    }

    @Override
    public FakeMetric initiateRequest() {
      FakeMetric metric = new FakeMetric();
      metrics.add(metric);
      return metric;
    }

    @Override
    public void reportFailure(FakeMetric metric, Throwable failure) {
      metric.failure = failure;
    }

    @Override
    public void reportRequestBegin(FakeMetric metric) {
      metric.requestBegin = System.currentTimeMillis();
    }

    @Override
    public void reportRequestEnd(FakeMetric metric) {
      metric.requestEnd = System.currentTimeMillis();
    }

    @Override
    public void reportResponseBegin(FakeMetric metric) {
      metric.responseBegin = System.currentTimeMillis();
    }

    @Override
    public void reportResponseEnd(FakeMetric metric) {
      metric.responseEnd = System.currentTimeMillis();
    }

  }

  public static class FakeMetric {

    public long requestBegin;
    public long requestEnd;
    public long responseBegin;
    public long responseEnd;
    public Throwable failure;

    public long requestBegin() {
      return requestBegin;
    }

    public long requestEnd() {
      return requestEnd;
    }

    public long responseBegin() {
      return responseBegin;
    }

    public long responseEnd() {
      return responseEnd;
    }

    public Throwable failure() {
      return failure;
    }
  }
}
