package io.vertx.test.fakeresolver;

import java.util.List;

public class FakeMetric {
  final FakeEndpoint endpoint;
  long requestBegin;
  long requestEnd;
  long responseBegin;
  long responseEnd;
  Throwable failure;

  FakeMetric(FakeEndpoint endpoint) {
    this.endpoint = endpoint;
  }

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
