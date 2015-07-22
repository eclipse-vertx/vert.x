package io.vertx.core.cli.commands;

import io.vertx.core.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class VertxIsolatedDeployer {

  private static final Logger log = LoggerFactory.getLogger(RunCommand.class);
  private Object main;

  public void deploy(String verticle, Vertx vertx, DeploymentOptions options, String message, Object main) {
    this.main = main;

    vertx.deployVerticle(verticle, options, createLoggingHandler(message, res -> {
      if (res.failed()) {
        // Failed to deploy
        handleDeployFailed(res.cause(), vertx, verticle, options);
      }
    }));
  }

  private void handleDeployFailed(Throwable cause, Vertx vertx, String mainVerticle, DeploymentOptions deploymentOptions) {
    if (main instanceof VertxLifeycleHooks) {
      ((VertxLifeycleHooks) main).handleDeployFailed(vertx, mainVerticle, deploymentOptions, cause);
    }
  }

  private <T> AsyncResultHandler<T> createLoggingHandler(final String message, final Handler<AsyncResult<T>> completionHandler) {
    return res -> {
      if (res.failed()) {
        Throwable cause = res.cause();
        if (cause instanceof VertxException) {
          VertxException ve = (VertxException) cause;
          log.error(ve.getMessage());
          if (ve.getCause() != null) {
            log.error(ve.getCause());
          }
        } else {
          log.error("Failed in " + message, cause);
        }
      } else {
        log.info("Succeeded in " + message);
      }
      if (completionHandler != null) {
        completionHandler.handle(res);
      }
    };
  }
}
