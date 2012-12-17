/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.deploy;

import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.deploy.impl.VerticleManager;

import java.io.File;
import java.net.URL;
import java.util.Map;

/**
 * This class represents the container in which a verticle runs.<p>
 * An instance of this class will be created by the system and made available to
 * a running Verticle.
 * It contains methods to programmatically deploy other verticles, undeploy
 * verticles, deploy modules, get the configuration for a verticle and get the logger for a
 * verticle.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Container {

  private final VerticleManager mgr;
  
  public Container(final VerticleManager mgr) {
    this.mgr = mgr;    
  }

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   */
  public void deployWorkerVerticle(String main) {
    deployWorkerVerticle(main, null, 1);
  }

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   */
  public void deployWorkerVerticle(String main, int instances) {
    deployWorkerVerticle(main, null, instances);
  }

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   */
  public void deployWorkerVerticle(String main, JsonObject config) {
    deployWorkerVerticle(main, config, 1);
  }

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   */
  public void deployWorkerVerticle(String main, JsonObject config, int instances) {
    deployWorkerVerticle(main, config, instances, null);
  }

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   * @param doneHandler The handler will be called passing in the unique deployment id when  deployment is complete
   */
  public void deployWorkerVerticle(String main, JsonObject config, int instances, Handler<String> doneHandler) {
    URL[] currURLs = mgr.getDeploymentURLs();
    File modDir = mgr.getDeploymentModDir();
    mgr.deployVerticle(true, main, config, currURLs, instances, modDir, null, doneHandler);
  }

  /**
   * Deploy a module programmatically
   * @param moduleName The main of the module to deploy
   */
  public void deployModule(String moduleName) {
    deployModule(moduleName, null, 1);
  }

  /**
   * Deploy a module programmatically
   * @param moduleName The main of the module to deploy
   * @param instances The number of instances to deploy (defaults to 1)
   */
  public void deployModule(String moduleName, int instances) {
    deployModule(moduleName, null, instances);
  }

  /**
   * Deploy a module programmatically
   * @param moduleName The main of the module to deploy
   * @param config JSON config to provide to the module
   */
  public void deployModule(String moduleName, JsonObject config) {
    deployModule(moduleName, config, 1);
  }

  /**
   * Deploy a module programmatically
   * @param moduleName The main of the module to deploy
   * @param config JSON config to provide to the module
   * @param instances The number of instances to deploy (defaults to 1)
   */
  public void deployModule(String moduleName, JsonObject config, int instances) {
    deployModule(moduleName, config, instances, null);
  }

  /**
   * Deploy a module programmatically
   * @param moduleName The main of the module to deploy
   * @param config JSON config to provide to the module
   * @param instances The number of instances to deploy (defaults to 1)
   * @param doneHandler The handler will be called passing in the unique deployment id when  deployment is complete
   */
  public void deployModule(String moduleName, JsonObject config, int instances, Handler<String> doneHandler) {
    File modDir = mgr.getDeploymentModDir();
    mgr.deployMod(moduleName, config, instances, modDir, doneHandler);
  }

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   */
  public void deployVerticle(String main) {
    deployVerticle(main, null, 1);
  }

  /**
   * Deploy a verticle programmatically
   * @param main The main of the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   */
  public void deployVerticle(String main, int instances) {
    deployVerticle(main, null, instances);
  }

  /**
   * Deploy a verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   */
  public void deployVerticle(String main, JsonObject config) {
    deployVerticle(main, config, 1);
  }

  /**
   * Deploy a verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   */
  public void deployVerticle(String main, JsonObject config, int instances) {
    deployVerticle(main, config, instances, null);
  }

  /**
   * Deploy a verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   * @param doneHandler The handler will be called passing in the unique deployment id when  deployment is complete
   */
  public void deployVerticle(String main, JsonObject config, int instances, Handler<String> doneHandler) {
    URL[] currURLs = mgr.getDeploymentURLs();
    File modDir = mgr.getDeploymentModDir();
    mgr.deployVerticle(false, main, config, currURLs, instances, modDir, null, doneHandler);
  }

  /**
   * Undeploy a verticle
   * @param deploymentID The deployment ID
   */
  public void undeployVerticle(String deploymentID) {
    undeployVerticle(deploymentID, null);
  }

  /**
   * Undeploy a module
   * @param deploymentID The deployment ID
   * @param doneHandler The handler will be called when undeployment is complete
   */
  public void undeployVerticle(String deploymentID, Handler<Void> doneHandler) {
    mgr.undeploy(deploymentID, doneHandler);
  }

  /**
   * Undeploy a module
   * @param deploymentID The deployment ID
   */
  public void undeployModule(String deploymentID) {
    undeployModule(deploymentID, null);
  }

  /**
   * Undeploy a module
   * @param deploymentID The deployment ID
   * @param doneHandler The handler will be called when undeployment is complete
   */
  public void undeployModule(String deploymentID, Handler<Void> doneHandler) {
    mgr.undeploy(deploymentID, doneHandler);
  }

  /**
   * Get the verticle configuration
   * @return a JSON object representing the configuration
   */
  public JsonObject getConfig() {
    return mgr.getConfig();
  }

  /**
   * Get the verticle logger
   * @return The logger
   */
  public Logger getLogger() {
    return mgr.getLogger();
  }

  /**
   * Cause the container to exit
   */
  public void exit() {
    mgr.unblock();
  }

  /**
   * Get an umodifiable map of system, environment variables.
   * @return The map
   */
  public Map<String, String> getEnv() {
    return System.getenv();
  }

}
