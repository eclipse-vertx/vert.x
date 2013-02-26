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

package org.vertx.java.platform;

import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;

import java.util.Map;

/**
 * This class represents the verticle's view of the container.<p>
 * An instance of this class will be created by the system and made available to
 * a running Verticle.
 * It contains methods to programmatically deploy other verticles, undeploy
 * verticles, deploy modules, get the configuration for a verticle and get the logger for a
 * verticle.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface Container {

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   */
  void deployWorkerVerticle(String main);

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   */
  void deployWorkerVerticle(String main, int instances);

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   */
  void deployWorkerVerticle(String main, JsonObject config);

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   */
  void deployWorkerVerticle(String main, JsonObject config, int instances);

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   * @param multiThreaded if true then the verticle will be deployed as a multi-threaded worker
   */
  void deployWorkerVerticle(String main, JsonObject config, int instances, boolean multiThreaded);

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   * @param doneHandler The handler will be called passing in the unique deployment id when  deployment is complete
   */
  void deployWorkerVerticle(String main, JsonObject config, int instances, boolean multiThreaded, Handler<String> doneHandler);

  /**
   * Deploy a module programmatically
   * @param moduleName The main of the module to deploy
   */
  void deployModule(String moduleName);

  /**
   * Deploy a module programmatically
   * @param moduleName The main of the module to deploy
   * @param instances The number of instances to deploy (defaults to 1)
   */
  void deployModule(String moduleName, int instances);

  /**
   * Deploy a module programmatically
   * @param moduleName The main of the module to deploy
   * @param config JSON config to provide to the module
   */
  void deployModule(String moduleName, JsonObject config);

  /**
   * Deploy a module programmatically
   * @param moduleName The main of the module to deploy
   * @param config JSON config to provide to the module
   * @param instances The number of instances to deploy (defaults to 1)
   */
  void deployModule(String moduleName, JsonObject config, int instances);

  /**
   * Deploy a module programmatically
   * @param moduleName The main of the module to deploy
   * @param config JSON config to provide to the module
   * @param instances The number of instances to deploy (defaults to 1)
   * @param doneHandler The handler will be called passing in the unique deployment id when  deployment is complete
   */
  void deployModule(String moduleName, JsonObject config, int instances, Handler<String> doneHandler);

  /**
   * Deploy a module programmatically
   * @param moduleName The main of the module to deploy
   * @param doneHandler The handler will be called passing in the unique deployment id when  deployment is complete
   */
  void deployModule(String moduleName, Handler<String> doneHandler);

  /**
   * Deploy a module programmatically
   * @param moduleName The main of the module to deploy
   * @param config JSON config to provide to the module
   * @param doneHandler The handler will be called passing in the unique deployment id when  deployment is complete
   */
  void deployModule(String moduleName, JsonObject config, Handler<String> doneHandler);

  /**
   * Deploy a module programmatically
   * @param moduleName The main of the module to deploy
   *                   * @param instances The number of instances to deploy (defaults to 1)
   * @param doneHandler The handler will be called passing in the unique deployment id when  deployment is complete
   */
  void deployModule(String moduleName, int instances, Handler<String> doneHandler);

  /**
   * Deploy a worker verticle programmatically
   * @param main The main of the verticle
   */
  void deployVerticle(String main);

  /**
   * Deploy a verticle programmatically
   * @param main The main of the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   */
  void deployVerticle(String main, int instances);

  /**
   * Deploy a verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   */
  void deployVerticle(String main, JsonObject config);

  /**
   * Deploy a verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   */
  void deployVerticle(String main, JsonObject config, int instances);

  /**
   * Deploy a verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   * @param instances The number of instances to deploy (defaults to 1)
   * @param doneHandler The handler will be called passing in the unique deployment id when  deployment is complete
   */
  void deployVerticle(String main, JsonObject config, int instances, Handler<String> doneHandler);

  /**
   * Deploy a verticle programmatically
   * @param main The main of the verticle
   * @param doneHandler The handler will be called passing in the unique deployment id when  deployment is complete
   */
  void deployVerticle(String main, Handler<String> doneHandler);

  /**
   * Deploy a verticle programmatically
   * @param main The main of the verticle
   * @param config JSON config to provide to the verticle
   * @param doneHandler The handler will be called passing in the unique deployment id when  deployment is complete
   */
  void deployVerticle(String main, JsonObject config, Handler<String> doneHandler);

  /**
   * Deploy a verticle programmatically
   * @param main The main of the verticle
   * @param doneHandler The handler will be called passing in the unique deployment id when  deployment is complete
   */
  void deployVerticle(String main, int instances, Handler<String> doneHandler);

  /**
   * Undeploy a verticle
   * @param deploymentID The deployment ID
   */
  void undeployVerticle(String deploymentID);

  /**
   * Undeploy a module
   * @param deploymentID The deployment ID
   * @param doneHandler The handler will be called when undeployment is complete
   */
  void undeployVerticle(String deploymentID, Handler<Void> doneHandler);

  /**
   * Undeploy a module
   * @param deploymentID The deployment ID
   */
  void undeployModule(String deploymentID);

  /**
   * Undeploy a module
   * @param deploymentID The deployment ID
   * @param doneHandler The handler will be called when undeployment is complete
   */
  void undeployModule(String deploymentID, Handler<Void> doneHandler);

  /**
   * Get the verticle configuration
   * @return a JSON object representing the configuration
   */
  JsonObject getConfig();

  /**
   * Get the verticle logger
   * @return The logger
   */
  Logger getLogger();

  /**
   * Cause the container to exit
   */
  void exit();

  /**
   * Get an umodifiable map of system, environment variables.
   * @return The map
   */
  Map<String, String> getEnv();

}
