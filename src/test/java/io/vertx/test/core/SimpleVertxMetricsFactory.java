package io.vertx.test.core;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.core.spi.metrics.VertxMetrics;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class SimpleVertxMetricsFactory<M extends VertxMetrics> implements VertxMetricsFactory {

  final M instance;

  public SimpleVertxMetricsFactory(M instance) {
    this.instance = instance;
  }

  @Override
  public VertxMetrics metrics(Vertx vertx, VertxOptions options) {
    return instance;
  }
}
