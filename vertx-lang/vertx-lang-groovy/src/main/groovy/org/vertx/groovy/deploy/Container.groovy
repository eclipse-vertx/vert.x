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

package org.vertx.groovy.deploy

import org.vertx.java.core.Handler
import org.vertx.java.core.json.JsonObject
import org.vertx.java.core.logging.Logger

/**
 * This class represents the scontainer in which a verticle runs.<p>
 * An instance of this class will be created by the system and made available to
 * a running Verticle.
 * It contains methods to programmatically deploy other verticles, undeploy
 * verticles, get the configuration for a verticle and get the logger for a
 * verticle.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class Container {

  private jContainer

  Container(jContainer) {
    this.jContainer = jContainer
  }

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   * @return Unique deployment id
   */
  String deployWorkerVerticle(String main) {
    jContainer.deployWorkerVerticle(main)
  }

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   * @param doneHandler The handler will be called passing in the unique deployment id when deployment is complete
   */
  void deployWorkerVerticle(String main, Closure doneHandler) {
    jContainer.deployWorkerVerticle(main, null, 1, doneHandler as Handler)
  }

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   */
  void deployWorkerVerticle(String main, int instances) {
    jContainer.deployWorkerVerticle(main, instances)
  }

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   */
  void deployWorkerVerticle(String main, Map<String, Object> config) {
    jContainer.deployWorkerVerticle(main, new JsonObject(config))
  }

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   */
  void deployWorkerVerticle(String main, Map<String, Object> config, int instances) {
    jContainer.deployWorkerVerticle(main, new JsonObject(config), instances)
  }

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   * @param doneHandler The handler will be called passing in the unique deployment id when deployment is complete
   */
  void deployWorkerVerticle(String main, Map<String, Object> config, int instances, Closure doneHandler) {
    jContainer.deployWorkerVerticle(main, new JsonObject(config), instances, doneHandler as Handler)
  }

  /**
   * Deploy a verticle programmatically
   * @param main The main of the verticle
   */
  void deployVerticle(String main) {
    jContainer.deployVerticle(main)
  }

  /**
   * Deploy a verticle programmatically
   * @param main The main of the verticle
   * @param doneHandler The handler will be called when deployment is complete
   */
  void deployVerticle(String main, Closure doneHandler) {
    jContainer.deployVerticle(main, null, 1, doneHandler as Handler)
  }

  /**
   * Deploy a verticle programmatically
   * @param main The main of the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   */
  void deployVerticle(String main, int instances) {
    jContainer.deployVerticle(main, instances)
  }

  /**
   * Deploy a verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   */
  void deployVerticle(String main, Map<String, Object> config) {
    jContainer.deployVerticle(main, new JsonObject(config))
  }

  /**
   * Deploy a verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   */
  void deployVerticle(String main, Map<String, Object> config, int instances) {
    jContainer.deployVerticle(main, new JsonObject(config), instances)
  }

  /**
   * Deploy a verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   * @param doneHandler The handler will be called passing in the unique deployment id when deployment is complete
   */
  void deployVerticle(String main, Map<String, Object> config, int instances, Closure doneHandler) {
    jContainer.deployVerticle(main, new JsonObject(config), instances, doneHandler as Handler)
  }

  /**
   * Deploy a module programmatically
   * @param moduleName The main of the module
   */
  void deployModule(String moduleName) {
    jContainer.deployModule(moduleName)
  }

  /**
   * Deploy a module programmatically
   * @param moduleName The main of the module
   * @param doneHandler The handler will be called when deployment is complete
   */
  void deployModule(String moduleName, Closure doneHandler) {
    jContainer.deployModule(moduleName, null, 1, doneHandler as Handler)
  }

  /**
   * Deploy a module programmatically
   * @param moduleName The main of the module
   * @param instances The number of instances to deploy (defaults to 1)
   */
  void deployModule(String moduleName, int instances) {
    jContainer.deployModule(moduleName, instances)
  }

  /**
   * Deploy a module programmatically
   * @param moduleName The main of the module
   * @param config JSON config to provide to the module
   */
  void deployModule(String moduleName, Map<String, Object> config) {
    jContainer.deployModule(moduleName, new JsonObject(config))
  }

  /**
   * Deploy a module programmatically
   * @param moduleName The main of the module
   * @param config JSON config to provide to the module
   * @param instances The number of instances to deploy (defaults to 1)
   */
  void deployModule(String moduleName, Map<String, Object> config, int instances) {
    jContainer.deployModule(moduleName, new JsonObject(config), instances)
  }

  /**
   * Deploy a module programmatically
   * @param moduleName The main of the module
   * @param config JSON config to provide to the module
   * @param instances The number of instances to deploy (defaults to 1)
   * @param doneHandler The handler will be called passing in the unique deployment id when deployment is complete
   */
  void deployModule(String moduleName, Map<String, Object> config, int instances, Closure doneHandler) {
    jContainer.deployModule(moduleName, new JsonObject(config), instances, doneHandler as Handler)
  }

  /**
   * Undeploy a verticle
   * @param deploymentID The deployment ID
   */
  void undeployVerticle(String deploymentID) {
    jContainer.undeployVerticle(deploymentID)
  }

  /**
   * Undeploy a verticle
   * @param deploymentID The deployment ID
   * @param doneHandler The handler will be called when undeployment is complete
   */
  void undeployVerticle(String deploymentID, Closure doneHandler) {
    jContainer.undeployVerticle(deploymentID, doneHandler as Handler)
  }

  /**
   * Undeploy a module
   * @param deploymentID The deployment ID
   */
  void undeployModule(String deploymentID) {
    jContainer.undeployModule(deploymentID)
  }

  /**
   * Undeploy a module
   * @param deploymentID The deployment ID
   * @param doneHandler The handler will be called when undeployment is complete
   */
  void undeployModule(String deploymentID, Closure doneHandler) {
    jContainer.undeployModule(deploymentID, doneHandler as Handler)
  }

  /**
   * Get the verticle configuration
   * @return a {@link java.util.Map} representing the JSON configuration
   */
  Map<String, Object> getConfig() {
    jContainer.getConfig().toMap()
  }

  /**
   * Get the verticle logger
   * @return The logger
   */
  Logger getLogger() {
    jContainer.getLogger()
  }

  /**
   * Cause the scontainer to exit
   */
  void exit() {
    jContainer.exit()
  }

  /**
   * Get an umodifiable map of system, environment variables.
   * @return The map
   */
  Map<String, String> getEnv() {
    jContainer.getEnv()
  }
}
