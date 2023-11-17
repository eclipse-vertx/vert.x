package io.vertx.core;

import io.vertx.core.impl.VertxInternal;
import io.vertx.test.core.AsyncTestBase;
import io.vertx.test.fakemetrics.FakeVertxMetrics;
import io.vertx.test.faketracer.FakeTracer;
import org.junit.Test;

public class VertxBuilderTest extends AsyncTestBase {

  @Test
  public void testBuildVertx() {
    Vertx vertx = Vertx.builder().build();
    vertx.setTimer(10, id -> {
      testComplete();
    });
    await();
    vertx.close();
  }

  @Test
  public void testTracerFactoryDoesNotRequireOptions() {
    FakeTracer tracer = new FakeTracer();
    Vertx vertx = Vertx.builder().withTracer(options -> tracer).build();
    assertEquals(tracer, ((VertxInternal)vertx).tracer());
  }

  @Test
  public void testMetricsFactoryDoesNotRequireOptions() {
    FakeVertxMetrics metrics = new FakeVertxMetrics();
    Vertx vertx = Vertx.builder().withMetrics(options -> metrics).build();
    assertEquals(metrics, ((VertxInternal)vertx).metricsSPI());
  }
}
