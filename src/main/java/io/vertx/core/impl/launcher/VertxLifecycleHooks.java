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
import io.vertx.core.json.JsonObject;

/**
 * Interface that let sub-classes of launcher to be notified on different events.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public interface VertxLifecycleHooks {

  /**
   * Hook for sub-classes of the {@link io.vertx.core.Launcher} class before the vertx instance is started. Options
   * can still be updated.
   *
   * @param config the json config file passed via -conf on the command line, an empty json object is not set.
   */
  void afterConfigParsed(JsonObject config);

  /**
   * Hook for sub-classes of the {@link io.vertx.core.Launcher} class before the vertx instance is started. Options
   * can still be updated.
   *
   * @param options the vert.x options
   */
  void beforeStartingVertx(VertxOptions options);

  /**
   * Hook for sub-classes of the {@link io.vertx.core.Launcher} class after the vertx instance is started.
   *
   * @param vertx the vert.x instance
   */
  void afterStartingVertx(Vertx vertx);

  /**
   * Hook for sub classes of the {@link io.vertx.core.Launcher} class before the verticle is deployed. Deployment
   * options can still be updated.
   *
   * @param deploymentOptions the deployment options
   */
  void beforeDeployingVerticle(DeploymentOptions deploymentOptions);

  /**
   * Hook for sub classes of the {@link io.vertx.core.Launcher} class called before the {@link Vertx} instance is
   * terminated. The hook is called during the {@link Vertx#close()} method.
   *
   * @param vertx the {@link Vertx} instance, cannot be {@code null}
   */
  void beforeStoppingVertx(Vertx vertx);

  /**
   * Hook for sub classes of the {@link io.vertx.core.Launcher} class called after the {@link Vertx} instance has been
   * terminated. The hook is called after the {@link Vertx#close()} method.
   */
  void afterStoppingVertx();

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
