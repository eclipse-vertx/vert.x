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

package org.vertx.java.platform;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.json.JsonObject;

import java.net.URL;
import java.util.Map;

/**
 *
 * Represents the Vert.x platform.<p>
 * It's the role of a PlatformManager to deploy and undeploy modules and verticles. It's also used to install
 * modules, and for various other tasks.<p>
 * The Platform Manager basically represents the Vert.x container in which verticles and modules run.<p>
 * The Platform Manager is used by the Vert.x CLI to run/install/etc modules and verticles but you could also
 * use it if you want to embed the entire Vert.x container in an application, or write some other tool (e.g.
 * a build tool, or a test tool) which needs to do stuff with the container.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
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
                      Handler<AsyncResult<String>> doneHandler);

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
                            Handler<AsyncResult<String>> doneHandler);

  /**
   * Deploy a module
   * @param moduleName The name of the module to deploy
   * @param config Any JSON config to pass to the verticle, or null if none
   * @param instances The number of instances to deploy
   * @param doneHandler Handler will be called with deploymentID when deployed, or null if it fails to deploy
   */
  void deployModule(String moduleName, JsonObject config,
                    int instances, Handler<AsyncResult<String>> doneHandler);

  /**
   * Deploy a module
   * @param moduleName The name of the module to deploy
   * @param config Any JSON config to pass to the verticle, or null if none
   * @param instances The number of instances to deploy
   * @param ha If true then the module is enabled for ha and will failover to any other vert.x instances
   *           with the same group in the cluster
   * @param doneHandler Handler will be called with deploymentID when deployed, or null if it fails to deploy
   */
  void deployModule(String moduleName, JsonObject config,
                    int instances,
                    boolean ha, Handler<AsyncResult<String>> doneHandler);

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
                           int instances, Handler<AsyncResult<String>> doneHandler);

  /**
   * Deploy a module from the classpath.
   * The classpath must contain a single mod.json and the resources for that module only.
   * @param moduleName The name of the module to deploy
   * @param config Any JSON config to pass to the verticle, or null if none
   * @param instances The number of instances to deploy
   * @param classpath Array of URLS corresponding to the classpath for the module
   * @param doneHandler Handler will be called with deploymentID when deployed, or null if it fails to deploy
   */
  void deployModuleFromClasspath(String moduleName, JsonObject config,
                                 int instances, URL[] classpath,
                                 Handler<AsyncResult<String>> doneHandler);

  /**
   * Undeploy a deployment
   * @param deploymentID The ID of the deployment to undeploy, as given in the doneHandler when deploying
   * @param doneHandler The done handler will be called when deployment is complete or fails
   */
  void undeploy(String deploymentID, Handler<AsyncResult<Void>> doneHandler);

  /**
   * Undeploy all verticles and modules
   * @param doneHandler The done handler will be called when complete
   */
  void undeployAll(Handler<AsyncResult<Void>> doneHandler) ;

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
  void installModule(String moduleName, Handler<AsyncResult<Void>> doneHandler);

  /**
   * Uninstall a module from the filesystem
   * @param moduleName
   */
  void uninstallModule(String moduleName, Handler<AsyncResult<Void>> doneHandler);

  /**
   * Pull in all the dependencies (the 'includes' and the 'deploys' fields in mod.json) and copy them into an
   * internal mods directory in the module. This allows a self contained module to be created.
   * @param moduleName The name of the module
   */
  void pullInDependencies(String moduleName, Handler<AsyncResult<Void>> doneHandler);


  /**
   * Create a fat executable jar which includes the Vert.x binaries and the module so it can be run
   * directly with java without having to pre-install Vert.x. e.g.:
   * java -jar mymod~1.0-fat.jar
   * @param moduleName The name of the module to create the fat jar for
   * @param outputDirectory Directory in which to place the jar
   * @param doneHandler Handler that will be called on completion
   */
  void makeFatJar(String moduleName, String outputDirectory, Handler<AsyncResult<Void>> doneHandler);

  void createModuleLink(String moduleName, Handler<AsyncResult<Void>> doneHandler);

  /**
   * Register a handler that will be called when the platform exits because of a verticle calling container.exit()
   * @param handler The handler
   */
  void registerExitHandler(Handler<Void> handler);

  /**
   * @return A reference to the Vertx instance used by the platform manager
   */
  Vertx vertx();

  /**
   * Stop the platform manager
   */
  void stop();

}
