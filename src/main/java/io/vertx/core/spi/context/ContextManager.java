/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.spi.context;

import io.vertx.core.Context;
import io.vertx.core.Vertx;

/**
 * A context manager that will propagate the vert.x context when it's being
 * used outside of managed vert.x threads, e.g. in a different thread pool.
 *
 * The context manager guarantees that vert.x operations running in the
 * same "execution trace" share the same context to avoid race conditions
 * when executing vert.x handlers.
 *
 * The default implementation of this context manager propagates the context
 * using a thread local, so that code running subsequent to the first context
 * creation can access it. Users can bring their own manager implementation
 * to use a different mechanism different to thread locals.
 */
public interface ContextManager {

  /**
   * Gets the context stored in this manager by the last `setContext`
   * operation, null otherwise.
   *
   * @param vertx is the vertx instance that wants to access the context.
   */
  Context getContext(Vertx vertx);

  /**
   * Sets the context stored in this manager.
   *
   * The manager implementation should not try to enrich or change the
   * underlying context as vert.x expects to get the same context it puts in.
   *
   * @param ctx is the context we want to store.
   */
  void setContext(Context ctx);
}
