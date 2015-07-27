/*
 *  Copyright (c) 2011-2013 The original author or authors
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

import io.vertx.core.impl.cli.VertxCommandLineInterface;
import io.vertx.core.impl.cli.VertxLifecycleHooks;


public class Launcher extends VertxCommandLineInterface implements VertxLifecycleHooks {

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
