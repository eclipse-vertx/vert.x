/*
 * Copyright (c) 2011-2013 The original author or authors
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

package org.vertx.java.platform.impl;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Container;

import java.util.Map;

public class DefaultContainer implements Container {

  private final PlatformManagerInternal mgr;

  DefaultContainer(final PlatformManagerInternal mgr) {
    this.mgr = mgr;
  }

  @Override
  public void deployWorkerVerticle(String main) {
    deployWorkerVerticle(main, null, 1);
  }

  @Override
  public void deployWorkerVerticle(String main, int instances) {
    deployWorkerVerticle(main, null, instances);
  }

  @Override
  public void deployWorkerVerticle(String main, JsonObject config) {
    deployWorkerVerticle(main, config, 1);
  }

  @Override
  public void deployWorkerVerticle(String main, JsonObject config, int instances) {
    deployWorkerVerticle(main, config, instances, false, null);
  }

  @Override
  public void deployWorkerVerticle(String main, JsonObject config, int instances, boolean multiThreaded) {
    deployWorkerVerticle(main, config, instances, multiThreaded, null);
  }

  @Override
  public void deployWorkerVerticle(String main, JsonObject config, int instances, boolean multiThreaded,
                                   Handler<AsyncResult<String>> doneHandler) {
    mgr.deployWorkerVerticle(multiThreaded, main, config, null, instances, null, doneHandler);
  }

  @Override
  public void deployModule(String moduleName) {
    deployModule(moduleName, null, 1);
  }

  @Override
  public void deployModule(String moduleName, int instances) {
    deployModule(moduleName, null, instances);
  }

  @Override
  public void deployModule(String moduleName, JsonObject config) {
    deployModule(moduleName, config, 1);
  }

  @Override
  public void deployModule(String moduleName, JsonObject config, int instances) {
    deployModule(moduleName, config, instances, null);
  }

  @Override
  public void deployModule(String moduleName, JsonObject config, int instances, Handler<AsyncResult<String>> doneHandler) {
    mgr.deployModule(moduleName, config, instances, doneHandler);
  }

  @Override
  public void deployModule(String moduleName, Handler<AsyncResult<String>> doneHandler) {
    mgr.deployModule(moduleName, null, 1, doneHandler);
  }

  @Override
  public void deployModule(String moduleName, JsonObject config, Handler<AsyncResult<String>> doneHandler) {
    mgr.deployModule(moduleName, config, 1, doneHandler);
  }

  @Override
  public void deployModule(String moduleName, int instances, Handler<AsyncResult<String>> doneHandler) {
    mgr.deployModule(moduleName, null, instances, doneHandler);
  }

  @Override
  public void deployVerticle(String main) {
    deployVerticle(main, null, 1);
  }

  @Override
  public void deployVerticle(String main, int instances) {
    deployVerticle(main, null, instances);
  }

  @Override
  public void deployVerticle(String main, JsonObject config) {
    deployVerticle(main, config, 1);
  }

  @Override
  public void deployVerticle(String main, JsonObject config, int instances) {
    deployVerticle(main, config, instances, null);
  }

  @Override
  public void deployVerticle(String main, JsonObject config, int instances, Handler<AsyncResult<String>> doneHandler) {
    mgr.deployVerticle(main, config, null, instances, null, doneHandler);
  }

  @Override
  public void deployVerticle(String main, Handler<AsyncResult<String>> doneHandler) {
    this.deployVerticle(main, null, 1, doneHandler);
  }

  @Override
  public void deployVerticle(String main, JsonObject config, Handler<AsyncResult<String>> doneHandler) {
    this.deployVerticle(main, config, 1, doneHandler);
  }

  @Override
  public void deployVerticle(String main, int instances, Handler<AsyncResult<String>> doneHandler) {
    this.deployVerticle(main, null, instances, doneHandler);
  }

  @Override
  public void undeployVerticle(String deploymentID) {
    undeployVerticle(deploymentID, null);
  }

  @Override
  public void undeployVerticle(String deploymentID, Handler<AsyncResult<Void>> doneHandler) {
    mgr.undeploy(deploymentID, doneHandler);
  }

  @Override
  public void undeployModule(String deploymentID) {
    undeployModule(deploymentID, null);
  }

  @Override
  public void undeployModule(String deploymentID, Handler<AsyncResult<Void>> doneHandler) {

    mgr.undeploy(deploymentID, doneHandler);
  }

  @Override
  public JsonObject config() {
    return mgr.config();
  }

  @Override
  public Logger logger() {
    return mgr.logger();
  }

  @Override
  public void exit() {
    mgr.exit();
  }

  @Override
  public Map<String, String> env() {
    return System.getenv();
  }

}
