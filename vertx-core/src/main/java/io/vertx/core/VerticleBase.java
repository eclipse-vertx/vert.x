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
import io.vertx.core.json.JsonObject;

/**
 *
 * An abstract base class that you can extend to write your own Verticle classes.
 * <p>
 * In the simplest case, just override the {@link #start()} method. If you have verticle clean-up to do you can
 * optionally override the {@link #stop()} method too.
 * This class also maintains references to the {@link Vertx} and {@link Context}
 * instances of the verticle for easy access.<p>
 * It also provides methods for getting the {@link #config verticle configuration} and {@link #deploymentID deployment ID}.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class VerticleBase implements Deployable {

  /**
   * Reference to the Vert.x instance that deployed this verticle
   */
  protected Vertx vertx;

  /**
   * Reference to the context of the verticle
   */
  protected Context context;

  /**
   * Initialise the verticle.<p>
   * This is called by Vert.x when the verticle instance is deployed. Don't call it yourself.
   * @param vertx  the deploying Vert.x instance
   * @param context  the context of the verticle
   */
  public void init(Vertx vertx, Context context) {
    this.vertx = vertx;
    this.context = context;
  }

  /**
   * Get the deployment ID of the verticle deployment
   * @return the deployment ID
   */
  public String deploymentID() {
    return context.deploymentID();
  }

  /**
   * Get the configuration of the verticle.
   * <p>
   * This can be specified when the verticle is deployed.
   * @return the configuration
   */
  public JsonObject config() {
    return context.config();
  }

  @Override
  public final Future<?> deploy(Context context) throws Exception {
    init(context.owner(), context);
    return start();
  }

  @Override
  public final Future<?> undeploy(Context context) throws Exception {
    return stop();
  }

  /**
   * Start the verticle.<p>
   * This is called by Vert.x when the verticle instance is deployed. Don't call it yourself.<p>
   * If your verticle does things in its startup which take some time then you can override this method
   * and call the startFuture some time later when start up is complete.
   * @return a future signalling the start-up completion
   */
  public Future<?> start() throws Exception {
    return ((ContextInternal)context).succeededFuture();
  }

  /**
   * Stop the verticle.<p>
   * This is called by Vert.x when the verticle instance is un-deployed. Don't call it yourself.<p>
   * @return a future signalling the clean-up completion
   */
  public Future<?> stop() throws Exception {
    return ((ContextInternal)context).succeededFuture();
  }
}
