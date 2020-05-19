/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.json.JsonObject;

import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface Deployment {

  boolean addChild(Deployment deployment);

  void removeChild(Deployment deployment);

  default void undeploy(Handler<AsyncResult<Void>> completionHandler) {
    undeploy().onComplete(completionHandler);
  }

  Future<Void> undeploy();

  default void doUndeploy(ContextInternal undeployingContext, Handler<AsyncResult<Void>> completionHandler) {
    doUndeploy(undeployingContext).onComplete(completionHandler);
  }

  Future<Void> doUndeploy(ContextInternal undeployingContext);

  JsonObject config();

  String deploymentID();

  String verticleIdentifier();

  DeploymentOptions deploymentOptions();

  Set<Context> getContexts();

  Set<Verticle> getVerticles();

  void undeployHandler(Handler<Void> handler);

  boolean isChild();

}
