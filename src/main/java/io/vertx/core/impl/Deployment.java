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

package io.vertx.core.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;

import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface Deployment {

  boolean addChild(Deployment deployment);

  void removeChild(Deployment deployment);

  void undeploy(Handler<AsyncResult<Void>> completionHandler);

  void doUndeploy(ContextInternal undeployingContext, Handler<AsyncResult<Void>> completionHandler);

  String deploymentID();

  String verticleIdentifier();

  DeploymentOptions deploymentOptions();

  Set<Verticle> getVerticles();

  boolean isChild();
}
