package io.vertx.core;

import io.vertx.core.cli.VertxCommandLineInterface;
import io.vertx.core.cli.commands.VertxLifeycleHooks;


public class Launcher extends VertxCommandLineInterface implements VertxLifeycleHooks {

  public static void main(String[] args) {
    new Launcher().dispatch(args);
  }

  /**
   * Hook for sub classes of {@link Starter} before the vertx instance is started.
   */
  public void beforeStartingVertx(VertxOptions options) {

  }

  /**
   * Hook for sub classes of {@link Starter} after the vertx instance is started.
   */
  public void afterStartingVertx(Vertx vertx) {

  }

  /**
   * Hook for sub classes of {@link Starter} before the verticle is deployed.
   */
  public void beforeDeployingVerticle(DeploymentOptions deploymentOptions) {

  }

  /**
   * A deployment failure has been encountered. You can override this method to customize the behavior.
   * By default it closes the `vertx` instance.
   */
  public void handleDeployFailed(Vertx vertx, String mainVerticle, DeploymentOptions deploymentOptions, Throwable cause) {
    // Default behaviour is to close Vert.x if the deploy failed
    vertx.close();
  }
}
