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
package io.vertx.core;

import io.vertx.core.internal.ContextInternal;

/**
 * Base interface for reactive services written in Java deployed in a Vert.x instance
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@FunctionalInterface
public interface Deployable {

  /**
   * Start the deployable.
   * <p>
   * Vert.x calls this method when deploying this deployable. You do not call it yourself.
   *
   * @param context the Vert.x context assigned to this deployable
   * @return a future signalling the start-up completion
   */
  Future<?> deploy(Context context) throws Exception;

  /**
   * Stop the deployable.
   * <p>
   * Vert.x calls this method when undeploying this deployable. You do not call it yourself.
   *
   * @param context the Vert.x context assigned to this deployable
   * @return a future signalling the clean-up completion
   */
  default Future<?> undeploy(Context context) throws Exception {
    return ((ContextInternal)context).succeededFuture();
  }
}
