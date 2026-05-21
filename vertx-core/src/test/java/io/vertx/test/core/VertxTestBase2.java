package io.vertx.test.core;

import io.vertx.core.Vertx;
import org.junit.Before;
import org.junit.runner.RunWith;


@RunWith(VertxRunner.class)
public class VertxTestBase2 {

  protected Vertx vertx;

  @Before
  public void setUp(Vertx vertx) throws Exception {
    this.vertx = vertx;
  }
}
