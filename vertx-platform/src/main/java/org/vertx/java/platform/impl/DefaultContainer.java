package org.vertx.java.platform.impl;

import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Container;

import java.util.Map;

/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
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
                                   AsyncResultHandler<String> doneHandler) {
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
  public void deployModule(String moduleName, JsonObject config, int instances, AsyncResultHandler<String> doneHandler) {
    mgr.deployModule(moduleName, config, instances, doneHandler);
  }

  @Override
  public void deployModule(String moduleName, AsyncResultHandler<String> doneHandler) {
    mgr.deployModule(moduleName, null, 1, doneHandler);
  }

  @Override
  public void deployModule(String moduleName, JsonObject config, AsyncResultHandler<String> doneHandler) {
    mgr.deployModule(moduleName, config, 1, doneHandler);
  }

  @Override
  public void deployModule(String moduleName, int instances, AsyncResultHandler<String> doneHandler) {
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
  public void deployVerticle(String main, JsonObject config, int instances, AsyncResultHandler<String> doneHandler) {
    mgr.deployVerticle(main, config, null, instances, null, doneHandler);
  }

  @Override
  public void deployVerticle(String main, AsyncResultHandler<String> doneHandler) {
    this.deployVerticle(main, null, 1, doneHandler);
  }

  @Override
  public void deployVerticle(String main, JsonObject config, AsyncResultHandler<String> doneHandler) {
    this.deployVerticle(main, config, 1, doneHandler);
  }

  @Override
  public void deployVerticle(String main, int instances, AsyncResultHandler<String> doneHandler) {
    this.deployVerticle(main, null, instances, doneHandler);
  }

  @Override
  public void undeployVerticle(String deploymentID) {
    undeployVerticle(deploymentID, null);
  }

  @Override
  public void undeployVerticle(String deploymentID, AsyncResultHandler<Void> doneHandler) {
    mgr.undeploy(deploymentID, doneHandler);
  }

  @Override
  public void undeployModule(String deploymentID) {
    undeployModule(deploymentID, null);
  }

  @Override
  public void undeployModule(String deploymentID, AsyncResultHandler<Void> doneHandler) {
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
