package io.vertx.core.impl.logic;

import io.vertx.core.Vertx;
import io.vertx.core.logic.AsyncLogic;

public class AsyncLogicImpl implements AsyncLogic {
  private final Vertx vertx;

  public AsyncLogicImpl(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public Vertx vertx() {
    return vertx;
  }
}
