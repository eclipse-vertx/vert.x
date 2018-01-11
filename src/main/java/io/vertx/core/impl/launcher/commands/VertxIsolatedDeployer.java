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

package io.vertx.core.impl.launcher.commands;

import io.vertx.core.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * A class isolating the deployment of verticle.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class VertxIsolatedDeployer {

  private static final Logger log = LoggerFactory.getLogger(VertxIsolatedDeployer.class);

  private String deploymentId;
  private Vertx vertx;

  /**
   * Deploys the given verticle.
   *
   * @param verticle          the verticle name
   * @param vertx             the vert.x instance
   * @param options           the deployment options
   * @param completionHandler the completion handler
   */
  public void deploy(String verticle, Vertx vertx, DeploymentOptions options,
                     Handler<AsyncResult<String>> completionHandler) {
    this.vertx = vertx;
    String message = (options.isWorker()) ? "deploying worker verticle" : "deploying verticle";
    vertx.deployVerticle(verticle, options, createHandler(message, completionHandler));
  }

  /**
   * Undeploys  the previously deployed verticle.
   *
   * @param completionHandler the completion handler
   */
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


  private Handler<AsyncResult<String>> createHandler(final String message,
                                                   final Handler<AsyncResult<String>>
                                                       completionHandler) {
    return res -> {
      if (res.failed()) {
        Throwable cause = res.cause();
        cause.printStackTrace();
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
