/*
 *  Copyright (c) 2011-2015 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *       The Eclipse Public License is available at
 *       http://www.eclipse.org/legal/epl-v10.html
 *
 *       The Apache License v2.0 is available at
 *       http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core;

import io.vertx.core.impl.launcher.VertxCommandLauncher;
import io.vertx.core.impl.launcher.VertxLifecycleHooks;


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
   * Hook for sub-classes of {@link Starter} before the vertx instance is started.
   */
  public void beforeStartingVertx(VertxOptions options) {

  }

  /**
   * Hook for sub-classes of {@link Starter} after the vertx instance is started.
   */
  public void afterStartingVertx(Vertx vertx) {

  }

  /**
   * Hook for sub-classes of {@link Starter} before the verticle is deployed.
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
