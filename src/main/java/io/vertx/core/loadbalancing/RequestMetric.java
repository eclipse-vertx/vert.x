package io.vertx.core.loadbalancing;

class RequestMetric {

  long requestBegin;
  long requestEnd;
  long responseBegin;
  long responseEnd;
  Throwable failure;

}
