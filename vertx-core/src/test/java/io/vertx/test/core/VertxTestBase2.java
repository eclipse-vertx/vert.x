package io.vertx.test.core;

import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(VertxRunner.class)
public class VertxTestBase2 {

  protected Vertx vertx;

  @Before
  public void setUp(Vertx vertx) {
    this.vertx = vertx;
  }
}
