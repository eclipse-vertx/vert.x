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

package io.vertx.core.internal.deployment;

import io.vertx.core.Future;
import io.vertx.core.internal.ContextInternal;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface DeploymentContext {

  /**
   * Add a child deployment
   *
   * @param deployment the child to add
   * @return whether the child could be added
   */
  boolean addChild(DeploymentContext deployment);

  /**
   * Remove a child deployment
   *
   * @param deployment the deployment to remove
   * @return whether the child was removed
   */
  boolean removeChild(DeploymentContext deployment);

  /**
   * @return whether it is a child deployment
   */
  boolean isChild();

  /**
   *
   * @param undeployingContext
   * @return
   */
  Future<Void> undeploy(ContextInternal undeployingContext);

  /**
   * @return the deployment ID
   */
  String id();

  Deployment deployment();

}
