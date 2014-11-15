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
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class AbstractVerticle implements Verticle, Context {

  protected Vertx vertx;
  protected Context context;

  @Override
  public Vertx getVertx() {
    return vertx;
  }

  @Override
  public void init(Vertx vertx, Context context) {
    this.vertx = vertx;
    this.context = context;
  }

  @Override
  public void runOnContext(Handler<Void> action) {
    context.runOnContext(action);
  }

  @Override
  public String deploymentID() {
    return context.deploymentID();
  }

  @Override
  public JsonObject config() {
    return context.config();
  }

  @Override
  public List<String> processArgs() {
    return context.processArgs();
  }

  @Override
  public boolean isEventLoopContext() {
    return context.isEventLoopContext();
  }

  @Override
  public boolean isWorker() {
    return context.isWorker();
  }

  @Override
  public boolean isMultiThreaded() {
    return context.isMultiThreaded();
  }

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    start();
    startFuture.complete();
  }

  @Override
  public void stop(Future<Void> stopFuture) throws Exception {
    stop();
    stopFuture.complete();
  }

  public void start() throws Exception {
  }

  public void stop() throws Exception {
  }

}
