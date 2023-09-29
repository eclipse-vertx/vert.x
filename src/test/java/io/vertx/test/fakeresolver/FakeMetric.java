package io.vertx.test.fakeresolver;

public class FakeMetric {
  final FakeEndpoint endpoint;
  long requestBegin;
  long requestEnd;
  long responseBegin;
  long responseEnd;

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
}
