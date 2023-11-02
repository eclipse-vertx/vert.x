package io.vertx.core.spi.loadbalancing;

class RequestMetric {

  long requestBegin;
  long requestEnd;
  long responseBegin;
  long responseEnd;
  Throwable failure;

}
