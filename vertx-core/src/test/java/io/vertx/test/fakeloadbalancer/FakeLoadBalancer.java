package io.vertx.test.fakeloadbalancer;

import io.vertx.core.net.endpoint.ServerEndpoint;
import io.vertx.core.net.endpoint.ServerSelector;
import io.vertx.core.net.endpoint.LoadBalancer;
import io.vertx.core.net.endpoint.InteractionMetrics;

import java.util.ArrayList;
import java.util.List;

public class FakeLoadBalancer implements LoadBalancer {

  List<? extends ServerEndpoint> endpoints;

  public List<? extends ServerEndpoint> endpoints() {
    return endpoints;
  }

  @Override
  public InteractionMetrics<?> newMetrics() {
    return new FakeLoadBalancerMetrics<>();
  }

  @Override
  public ServerSelector selector(List<? extends ServerEndpoint> listOfServers) {
    this.endpoints = listOfServers;
    return LoadBalancer.ROUND_ROBIN.selector(listOfServers);
  }

  public static class FakeLoadBalancerMetrics<E> implements InteractionMetrics<FakeMetric> {

    List<FakeMetric> metrics = new ArrayList<>();

    public List<FakeMetric> metrics2() {
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
