package org.vertx.java.platform;/*
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

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.json.JsonObject;

import java.net.URL;
import java.util.Map;

/**
 * Public interface for PlatformManager
 *
 * It's the role of a PlatformManager to deploy and undeploy modules and verticles. It's also used to install
 * modules, and for various other tasks.
 *
 * The Platform Manager basically represents the Vert.x container in which verticles and modules run.
 *
 * The Platform Manager is used by the Vert.x CLI to run/install/etc modules and verticles but you could also
 * use it if you want to embed the entire Vert.x container in an application, or write some other tool (e.g.
 * a build tool, or a test tool) which needs to do stuff with the container
 */
public interface PlatformManager {

  /**
   * Deploy a verticle
   * @param main The main, e.g. app.js, foo.rb, org.mycompany.MyMain, etc
   * @param config Any JSON config to pass to the verticle, or null if none
   * @param classpath The classpath for the verticle
   * @param instances The number of instances to deploy
   * @param includes Comma separated list of modules to include, or null if none
   * @param doneHandler Handler will be called with deploymentID when deployed, or null if it fails to deploy
   */
  void deployVerticle(String main,
                      JsonObject config, URL[] classpath,
                      int instances,
                      String includes,
                      Handler<String> doneHandler);

  /**
   * Deploy a worker verticle
   * @param multiThreaded Is it a multi-threaded worker verticle?
   * @param main The main, e.g. app.js, foo.rb, org.mycompany.MyMain, etc
   * @param config Any JSON config to pass to the verticle, or null if none
   * @param classpath The classpath for the verticle
   * @param instances The number of instances to deploy
   * @param includes Comma separated list of modules to include, or null if none
   * @param doneHandler Handler will be called with deploymentID when deployed, or null if it fails to deploy
   */
  void deployWorkerVerticle(boolean multiThreaded, String main,
                            JsonObject config, URL[] classpath,
                            int instances,
                            String includes,
                            Handler<String> doneHandler);

  /**
   * Deploy a module
   * @param moduleName The name of the module to deploy
   * @param config Any JSON config to pass to the verticle, or null if none
   * @param instances The number of instances to deploy
   * @param doneHandler Handler will be called with deploymentID when deployed, or null if it fails to deploy
   */
  void deployModule(String moduleName, JsonObject config,
                    int instances, Handler<String> doneHandler);

  /**
   * Deploy a module from a zip file.
   * The zip must contain a valid Vert.x module. Vert.x will automatically install the module from the zip into the
   * local mods dir or the system mods dir (if it's a system module), or VERTX_MODS if set, and then deploy the
   * module
   * @param zipFileName The name of the zip file that contains the module
   * @param config Any JSON config to pass to the verticle, or null if none
   * @param instances The number of instances to deploy
   * @param doneHandler Handler will be called with deploymentID when deployed, or null if it fails to deploy
   */
  void deployModuleFromZip(String zipFileName, JsonObject config,
                           int instances, Handler<String> doneHandler);

  /**
   * Undeploy a deployment
   * @param deploymentID The ID of the deployment to undeploy, as given in the doneHandler when deploying
   * @param doneHandler The done handler will be called when deployment is complete or fails
   */
  void undeploy(String deploymentID, Handler<Void> doneHandler);

  /**
   * Undeploy all verticles and modules
   * @param doneHandler The done handler will be called when complete
   */
  void undeployAll(Handler<Void> doneHandler) ;

  /**
   * List all deployments, with deployment ID and number of instances
   * @return map of instances
   */
  Map<String, Integer> listInstances();

  /**
   * Install a module into the filesystem
   * Vert.x will search in the configured repos to locate the module
   * @param moduleName The name of the module
   */
  void installModule(String moduleName);

  /**
   * Uninstall a module from the filesystem
   * @param moduleName
   */
  void uninstallModule(String moduleName);

  /**
   * Pull in all the dependencies (the 'includes' and the 'deploys' fields in mod.json) and copy them into an
   * internal mods directory in the module. This allows a self contained module to be created.
   * @param moduleName The name of the module
   * @return true if succeeded
   */
  boolean pullInDependencies(String moduleName);

  /**
   * Register a handler that will be called when the platform exits because of a verticle calling container.exit()
   * @param handler The handler
   */
  void registerExitHandler(Handler<Void> handler);

  /**
   * @return A reference to the Vertx instance used by the platform manager
   */
  Vertx getVertx();

  /**
   * Stop the platform manager
   */
  void stop();

  // debug only
  int checkNoModules();


}
