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

/**
 *
 * An abstract base class that you can extend to write your own Verticle classes.
 * <p>
 * Instead of implementing {@link Verticle} directly, it is often simpler to just extend this class.
 * <p>
 * In the simplest case, just override the {@link #start()} method. If you have verticle clean-up to do you can optionally
 * override the {@link #stop()} method too.
 * <p>
 * This class also maintains references to the {@link Vertx} and {@link Context} instances of the verticle for easy access.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class VerticleBase implements Verticle {

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
  public final Vertx getVertx() {
    return vertx;
  }

  /**
   * Initialise the verticle.<p>
   * This is called by Vert.x when the verticle instance is deployed. Don't call it yourself.
   * @param vertx  the deploying Vert.x instance
   * @param context  the context of the verticle
   */
  @Override
  public final void init(Vertx vertx, Context context) {
    this.vertx = vertx;
    this.context = context;
  }

  /**
   * This is called by Vert.x when the verticle instance is undeployed. Don't call it yourself.
   */
  @Override
  @SuppressWarnings("unchecked")
  public final void start(Promise<Void> startPromise) throws Exception {
    handle(start(), startPromise);
  }

  /**
   * This is called by Vert.x when the verticle instance is deployed. Don't call it yourself.<p>
   */
  @Override
  public final void stop(Promise<Void> stopPromise) throws Exception {
    handle(stop(), stopPromise);
  }

  private void handle(Future<?> completion, Promise<Void> promise) {
    if (completion != null) {
      completion.onComplete((Handler) promise);
    } else {
      promise.complete();
    }
  }

  /**
   * Override to implement Verticle start.
   *
   * @throws Exception any exception preventing the start of the verticle
   */
  public Future<?> start() throws Exception {
    return Future.succeededFuture();
  }

  /**
   * Override this put your clean-up code in here.
   *
   * @throws Exception any exception happening during the stop of the verticle
   */
  public Future<?> stop() throws Exception {
    return Future.succeededFuture();
  }
}
