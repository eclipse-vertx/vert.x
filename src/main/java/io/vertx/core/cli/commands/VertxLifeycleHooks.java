package io.vertx.core.cli.commands;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

public interface VertxLifeycleHooks {

  /**
   * Hook for sub-classes of the starter class before the vertx instance is started.
   *
   * @param options the vert.x options
   */
  void beforeStartingVertx(VertxOptions options);

  /**
   * Hook for sub-classes of the starter class after the vertx instance is started.
   *
   * @param vertx the vert.x instance
   */
  void afterStartingVertx(Vertx vertx);

  /**
   * Hook for sub classes of the starter class before the verticle is deployed.
   *
   * @param deploymentOptions the deployment options
   */
  void beforeDeployingVerticle(DeploymentOptions deploymentOptions);

  /**
   * A deployment failure has been encountered. You can override this method to customize the behavior.
   */
  void handleDeployFailed(Vertx vertx, String mainVerticle, DeploymentOptions deploymentOptions, Throwable cause);

}
