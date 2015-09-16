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
package io.vertx.core.impl.launcher;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

/**
 * Interface that let sub-classes of launcher to be notified on different events.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public interface VertxLifecycleHooks {

  /**
   * Hook for sub-classes of the starter class before the vertx instance is started. Options can still be updated.
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
   * Hook for sub classes of the starter class before the verticle is deployed. Deployment options can still be updated.
   *
   * @param deploymentOptions the deployment options
   */
  void beforeDeployingVerticle(DeploymentOptions deploymentOptions);

  /**
   * A deployment failure has been encountered. You can override this method to customize the behavior.
   *
   * @param vertx             the vert.x instance
   * @param mainVerticle      the main verticle name
   * @param deploymentOptions the deployment options
   * @param cause             the cause
   */
  void handleDeployFailed(Vertx vertx, String mainVerticle, DeploymentOptions deploymentOptions,
                          Throwable cause);

}
