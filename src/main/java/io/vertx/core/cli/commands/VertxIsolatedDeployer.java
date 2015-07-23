package io.vertx.core.cli.commands;

import io.vertx.core.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class VertxIsolatedDeployer {

  private static final Logger log = LoggerFactory.getLogger(RunCommand.class);

  private String deploymentId;
  private Vertx vertx;

  public void deploy(String verticle, Vertx vertx, DeploymentOptions options,
                     Handler<AsyncResult<String>> completionHandler) {
    this.vertx = vertx;
    String message = (options.isWorker()) ? "deploying worker verticle" : "deploying verticle";
    vertx.deployVerticle(verticle, options, createHandler(message, completionHandler));
  }

  public void undeploy(Handler<AsyncResult<Void>> completionHandler) {
    vertx.undeploy(deploymentId, res -> {
      if (res.failed()) {
        log.error("Failed in undeploying " + deploymentId, res.cause());
      } else {
        log.info("Succeeded in undeploying " + deploymentId);
      }
      deploymentId = null;
      completionHandler.handle(res);
    });
  }


  private AsyncResultHandler<String> createHandler(final String message,
                                                   final Handler<AsyncResult<String>>
      completionHandler) {
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
        deploymentId = res.result();
        log.info("Succeeded in " + message);
      }
      if (completionHandler != null) {
        completionHandler.handle(res);
      }
    };
  }
}
