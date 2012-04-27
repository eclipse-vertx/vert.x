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
 * This class represents the container in which a verticle runs.<p>
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
   * @param doneHandler The handler will be called when deployment is complete
   * @return Unique deployment id
   */
  String deployWorkerVerticle(String main, Closure doneHandler) {
    jContainer.deployWorkerVerticle(main, null, 1, doneHandler as Handler)
  }

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   * @return Unique deployment id
   */
  String deployWorkerVerticle(String main, int instances) {
    jContainer.deployWorkerVerticle(main, instances)
  }

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   * @return Unique deployment id
   */
  String deployWorkerVerticle(String main, Map<String, Object> config) {
    jContainer.deployWorkerVerticle(main, new JsonObject(config))
  }

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   * @return Unique deployment id
   */
  String deployWorkerVerticle(String main, Map<String, Object> config, int instances) {
    jContainer.deployWorkerVerticle(main, new JsonObject(config), instances)
  }

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   * @param doneHandler The handler will be called when deployment is complete
   * @return Unique deployment id
   */
  String deployWorkerVerticle(String main, Map<String, Object> config, int instances, Closure doneHandler) {
    jContainer.deployWorkerVerticle(main, new JsonObject(config), instances, doneHandler as Handler)
  }

  /**
   * Deploy a verticle programmatically
   * @param main The main of the verticle
   * @return Unique deployment id
   */
  String deployVerticle(String main) {
    jContainer.deployVerticle(main)
  }

  /**
   * Deploy a verticle programmatically
   * @param main The main of the verticle
   * @param doneHandler The handler will be called when deployment is complete
   * @return Unique deployment id
   */
  String deployVerticle(String main, Closure doneHandler) {
    jContainer.deployVerticle(main, null, 1, doneHandler as Handler)
  }

  /**
   * Deploy a verticle programmatically
   * @param main The main of the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   * @return Unique deployment id
   */
  String deployVerticle(String main, int instances) {
    jContainer.deployVerticle(main, instances)
  }

  /**
   * Deploy a verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   * @return Unique deployment id
   */
  String deployVerticle(String main, Map<String, Object> config) {
    jContainer.deployVerticle(main, new JsonObject(config))
  }

  /**
   * Deploy a verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   * @return Unique deployment id
   */
  String deployVerticle(String main, Map<String, Object> config, int instances) {
    jContainer.deployVerticle(main, new JsonObject(config), instances)
  }

  /**
   * Deploy a verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   * @param doneHandler The handler will be called when deployment is complete
   * @return Unique deployment id
   */
  String deployVerticle(String main, Map<String, Object> config, int instances, Closure doneHandler) {
    jContainer.deployVerticle(main, new JsonObject(config), instances, doneHandler as Handler)
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
}
