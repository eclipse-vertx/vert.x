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

package io.vertx.core;

import io.vertx.core.internal.ContextInternal;

/**
 * WARNING : this class is not deprecated, however we encourage instead to use {@link VerticleBase}
 *
 * A verticle is a piece of code that can be deployed by Vert.x.
 * <p>
 * Use of verticles with Vert.x is entirely optional, but if you use them they provide an <i>actor-like</i>
 * deployment and concurrency model, out of the box.
 * <p>
 * Vert.x does not provide a strict actor implementation, but there are significant similarities.
 * <p>
 * You can think of verticle instances as a bit like actors in the Actor Model. A typical verticle-based Vert.x application
 * will be composed of many verticle instances in each Vert.x instance.
 * <p>
 * The verticles communicate with each other by sending messages over the {@link io.vertx.core.eventbus.EventBus}.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface Verticle extends Deployable {

  /**
   * Get a reference to the Vert.x instance that deployed this verticle
   *
   * @return A reference to the Vert.x instance
   */
  Vertx getVertx();

  /**
   * Initialise the verticle with the Vert.x instance and the context.
   * <p>
   * This method is called by Vert.x when the instance is deployed. You do not call it yourself.
   *
   * @param vertx  the Vert.x instance
   * @param context the context
   */
  void init(Vertx vertx, Context context);

  /**
   * Start the verticle instance.
   * <p>
   * Vert.x calls this method when deploying the instance. You do not call it yourself.
   * <p>
   * A promise is passed into the method, and when deployment is complete the verticle should either call
   * {@link io.vertx.core.Promise#complete} or {@link io.vertx.core.Promise#fail} the future.
   *
   * @param startPromise  the future
   */
  void start(Promise<Void> startPromise) throws Exception;

  /**
   * Stop the verticle instance.
   * <p>
   * Vert.x calls this method when un-deploying the instance. You do not call it yourself.
   * <p>
   * A promise is passed into the method, and when un-deployment is complete the verticle should either call
   * {@link io.vertx.core.Promise#complete} or {@link io.vertx.core.Promise#fail} the future.
   *
   * @param stopPromise  the future
   */
  void stop(Promise<Void> stopPromise) throws Exception;
}
