package io.vertx.test.fakeloadbalancer;

import io.vertx.core.spi.loadbalancing.EndpointSelector;
import io.vertx.core.loadbalancing.LoadBalancer;
import io.vertx.core.spi.loadbalancing.EndpointMetrics;

import java.util.ArrayList;
import java.util.List;

public class FakeLoadBalancer implements LoadBalancer, EndpointSelector {

  List<FakeEndpointMetrics> endpoints;

  EndpointSelector actual = LoadBalancer.ROUND_ROBIN.selector();

  public List<FakeEndpointMetrics> endpoints() {
    return endpoints;
  }

  @Override
  public EndpointSelector selector() {
    return this;
  }

  @Override
  public FakeEndpointMetrics endpointMetrics() {
    return new FakeEndpointMetrics();
  }

  @Override
  public int selectEndpoint(List<EndpointMetrics<?>> endpoints) {
    this.endpoints = (List) endpoints;
    return actual.selectEndpoint(endpoints);
  }

  public static class FakeEndpointMetrics implements EndpointMetrics<FakeMetric> {

    List<FakeMetric> metrics = new ArrayList<>();

    public List<FakeMetric> metrics() {
      return metrics;
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
