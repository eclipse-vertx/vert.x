/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core;

import io.vertx.core.json.JsonObject;

import java.util.List;

/**
 *
 * An abstract base class that you can extend to write your own Verticle classes.
 * <p>
 * Instead of implementing {@link io.vertx.core.Verticle} directly it it often simpler to just extend this class.
 * <p>
 * In the simplest case, just override the {@link #start} method. If you have verticle clean-up to do you can
 * optionally override the {@link #stop} method too.
 * <p>If you're verticle does extra start-up or clean-up which takes some time (e.g. it deploys other verticles) then
 * you should override the asynchronous {@link #start(Future) start} and {@link #stop(Future) stop} methods.
 * <p>
 * This class also provides maintains references to the {@link io.vertx.core.Vertx} and {@link io.vertx.core.Context}
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
   * Get the arguments used when deploying the Vert.x process.
   * @return the list of arguments
   */
  public List<String> processArgs() {
    return context.processArgs();
  }

  /**
   * Start the verticle.<p>
   * This is called by Vert.x when the verticle instance is deployed. Don't call it yourself.<p>
   * If your verticle does things in it's startup which take some time then you can override this method
   * and call the startFuture some time later when start up is complete.
   * @param startFuture  a future which should be called when verticle start-up is complete.
   * @throws Exception
   */
  @Override
  public void start(Future<Void> startFuture) throws Exception {
    start();
    startFuture.complete();
  }

  /**
   * Stop the verticle.<p>
   * This is called by Vert.x when the verticle instance is un-deployed. Don't call it yourself.<p>
   * If your verticle does things in it's shut-down which take some time then you can override this method
   * and call the stopFuture some time later when clean-up is complete.
   * @param stopFuture  a future which should be called when verticle clean-up is complete.
   * @throws Exception
   */
  @Override
  public void stop(Future<Void> stopFuture) throws Exception {
    stop();
    stopFuture.complete();
  }

  /**
   * If your verticle does a simple, synchronous start-up then override this method and put your start-up
   * code in there.
   * @throws Exception
   */
  public void start() throws Exception {
  }

  /**
   * If your verticle has simple synchronous clean-up tasks to complete then override this method and put your clean-up
   * code in there.
   * @throws Exception
   */
  public void stop() throws Exception {
  }

}
