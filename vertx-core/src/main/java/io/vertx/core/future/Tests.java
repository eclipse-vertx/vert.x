package io.vertx.core.future;

import io.vertx.core.*;

public class Tests {

  public void useCase(Future<String> fut, Completable<Object> completable) {
    fut.onComplete(completable);
  }

  public void another(Future<?> fut, Completable<Object> completable) {
    fut.onComplete(completable);
  }

  public void another2(Future<String> fut, Completable<Object> completable) {
    fut.onComplete(completable);
  }

  public void lambda(Future<String> fut) {
    fut.onComplete((re, err) -> {

    });
  }

  public void varianceWithPromise(Future<String> fut, Promise<Object> completable) {
    fut.onComplete(completable);
  }

  public void testDeploymentAPI1(Vertx vertx) {

    vertx.deploy1(ctx -> Completable::succeed, new DeploymentOptions());

    vertx.deploy1(ctx -> completable -> vertx.timer(100).onComplete(completable), new DeploymentOptions());

  }

  public void testDeploymentAPI2(Vertx vertx) {

    vertx.deploy2(ctx -> Future::succeededFuture, new DeploymentOptions());

    vertx.deploy2(ctx -> () -> vertx.timer(100), new DeploymentOptions());

  }

}
