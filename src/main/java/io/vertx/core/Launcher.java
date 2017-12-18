/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core;

import io.vertx.core.impl.launcher.VertxCommandLauncher;
import io.vertx.core.impl.launcher.VertxLifecycleHooks;
import io.vertx.core.json.JsonObject;


/**
 * A {@code main()} class that can be used to create Vert.x instance and deploy a verticle, or run a bare Vert.x instance.
 * <p/>
 * This class is used by the {@code vertx} command line utility to deploy verticles from the command line.
 * It is extensible as "commands" can be added using the {@link io.vertx.core.spi.launcher.CommandFactory}
 * SPI.
 * <p/>
 * E.g.
 * <p/>
 * {@code vertx run myverticle.js}
 * {@code vertx my-command ...}
 * <p/>
 * It can also be used as the main class of an executable jar so you can run verticles directly with:
 * <p/>
 * {@code java -jar myapp.jar}
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class Launcher extends VertxCommandLauncher implements VertxLifecycleHooks {

  /**
   * Main entry point.
   *
   * @param args the user command line arguments.
   */
  public static void main(String[] args) {
    new Launcher().dispatch(args);
  }

  /**
   * Utility method to execute a specific command.
   *
   * @param cmd  the command
   * @param args the arguments
   */
  public static void executeCommand(String cmd, String... args) {
    new Launcher().execute(cmd, args);
  }

  /**
   * Hook for sub-classes of {@link Launcher} after the config has been parsed.
   *
   * @param config the read config, empty if none are provided.
   */
  public void afterConfigParsed(JsonObject config) {
  }

  /**
   * Hook for sub-classes of {@link Launcher} before the vertx instance is started.
   *
   * @param options the configured Vert.x options. Modify them to customize the Vert.x instance.
   */
  public void beforeStartingVertx(VertxOptions options) {

  }

  /**
   * Hook for sub-classes of {@link Launcher} after the vertx instance is started.
   *
   * @param vertx the created Vert.x instance
   */
  public void afterStartingVertx(Vertx vertx) {

  }

  /**
   * Hook for sub-classes of {@link Launcher} before the verticle is deployed.
   *
   * @param deploymentOptions the current deployment options. Modify them to customize the deployment.
   */
  public void beforeDeployingVerticle(DeploymentOptions deploymentOptions) {

  }

  @Override
  public void beforeStoppingVertx(Vertx vertx) {

  }

  @Override
  public void afterStoppingVertx() {

  }

  /**
   * A deployment failure has been encountered. You can override this method to customize the behavior.
   * By default it closes the `vertx` instance.
   *
   * @param vertx             the vert.x instance
   * @param mainVerticle      the verticle
   * @param deploymentOptions the verticle deployment options
   * @param cause             the cause of the failure
   */
  public void handleDeployFailed(Vertx vertx, String mainVerticle, DeploymentOptions deploymentOptions, Throwable cause) {
    // Default behaviour is to close Vert.x if the deploy failed
    vertx.close();
  }
}
