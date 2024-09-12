/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
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

import java.util.Collections;
import java.util.List;

/**
 * WARNING : this class is not deprecated, however we encourage instead to use {@link VerticleBase}
 *
 * An abstract base class that you can extend to write your own Verticle classes.
 * <p>
 * Instead of implementing {@link io.vertx.core.Verticle} directly, it is often simpler to just extend this class.
 * <p>
 * In the simplest case, just override the {@link #start(Promise)} method. If you have verticle clean-up to do you can
 * optionally override the {@link #stop(Promise)} method too.
 * <p>If your verticle does extra start-up or clean-up that takes some time (e.g. it deploys other verticles) then
 * you should override the asynchronous {@link #start(Promise) start} and {@link #stop(Promise) stop} methods.
 * <p>
 * This class also maintains references to the {@link io.vertx.core.Vertx} and {@link io.vertx.core.Context}
 * instances of the verticle for easy access.<p>
 * It also provides methods for getting the {@link #config verticle configuration}, {@link #processArgs process arguments},
 * and {@link #deploymentID deployment ID}.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class AbstractVerticle implements Verticle {


  /**
   * Reference to the Vert.x instance that deployed this verticle
   */
  protected Vertx vertx;

  /**
   * Reference to the context of the verticle
   */
  protected Context context;

  /**
   * Get the Vert.x instance
   * @return the Vert.x instance
   */
  @Override
  public Vertx getVertx() {
    return vertx;
  }

  /**
   * Initialise the verticle.<p>
   * This is called by Vert.x when the verticle instance is deployed. Don't call it yourself.
   * @param vertx  the deploying Vert.x instance
   * @param context  the context of the verticle
   */
  @Override
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

  /**
   * @return an empty list
   * @deprecated As of version 5, Vert.x is no longer tightly coupled to the CLI
   */
  @Deprecated
  public List<String> processArgs() {
    return Collections.emptyList();
  }

  @Override
  public final Future<?> deploy(Context context) {
    init(context.owner(), context);
    ContextInternal internal = (ContextInternal) context;
    Promise<Void> promise = internal.promise();
    try {
      start(promise);
    } catch (Throwable t) {
      if (!promise.tryFail(t)) {
        internal.reportException(t);
      }
    }
    return promise.future();
  }

  @Override
  public final Future<?> undeploy(Context context) {
    ContextInternal internal = (ContextInternal) context;
    Promise<Void> promise = internal.promise();
    try {
      stop(promise);
    } catch (Throwable t) {
      if (!promise.tryFail(t)) {
        internal.reportException(t);
      }
    }
    return promise.future();
  }

  /**
   * Start the verticle.<p>
   * This is called by Vert.x when the verticle instance is deployed. Don't call it yourself.<p>
   * If your verticle does things in its startup which take some time then you can override this method
   * and call the startFuture some time later when start up is complete.
   * @param startPromise  a promise which should be called when verticle start-up is complete.
   * @throws Exception
   */
  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    start();
    startPromise.complete();
  }

  /**
   * Stop the verticle.<p>
   * This is called by Vert.x when the verticle instance is un-deployed. Don't call it yourself.<p>
   * If your verticle does things in its shut-down which take some time then you can override this method
   * and call the stopFuture some time later when clean-up is complete.
   * @param stopPromise  a promise which should be called when verticle clean-up is complete.
   * @throws Exception
   */
  @Override
  public void stop(Promise<Void> stopPromise) throws Exception {
    stop();
    stopPromise.complete();
  }

  /**
   * If your verticle does a simple, synchronous start-up then override this method and put your start-up
   * code in here.
   * @throws Exception
   */
  public void start() throws Exception {
  }

  /**
   * If your verticle has simple synchronous clean-up tasks to complete then override this method and put your clean-up
   * code in here.
   * @throws Exception
   */
  public void stop() throws Exception {
  }

}
