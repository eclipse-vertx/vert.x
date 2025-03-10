/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.internal.deployment;

import io.vertx.core.*;

import java.util.*;

public interface Deployment {

  /**
   * @return the set of context of this deployment
   */
  Set<Context> contexts();

  /**
   * @return the set of deployable of this deployment
   */
  Set<Deployable> instances();

  /**
   * @return a copy of the deployment options
   */
  DeploymentOptions options();

  /**
   * @return the deployable identifier, e.g. java:MyVerticle
   */
  String identifier();

  /**
   * Deploy this deployment in its context
   * @param context the context
   * @return a future of the completion
   */
  Future<?> deploy(DeploymentContext context);

  /**
   * Undeploy this deployment
   *
   * @return a future of the completion
   */
  Future<?> undeploy();

  /**
   * Deployment cleanup.
   *
   * @return a future of the completion
   */
  Future<?> cleanup();

}
