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
import org.vertx.java.core.VertxFactory;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.impl.Deployment;

import java.io.File;
import java.net.URL;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Public interface for PlatformManager
 *
 * It's the role of a PlatformManager to deploy and undeploy modules and verticles. It's also used to install
 * modules, and for various other tasks.
 */
public interface PlatformManager {

  static PlatformManager instance = ServiceLoader.load(PlatformManagerFactory.class).iterator().next().createPlatformManager();

  void deployVerticle(final boolean worker, final boolean multiThreaded, final String main,
                      final JsonObject config, final URL[] urls,
                      final int instances, final File currentModDir,
                      final String includes,
                      final Handler<String> doneHandler);

  void deployMod(final String modName, final JsonObject config,
                 final int instances, final File currentModDir, final Handler<String> doneHandler);

  void undeploy(String deploymentID, final Handler<Void> doneHandler);

  void undeployAll(final Handler<Void> doneHandler) ;

  Map<String, Integer> listInstances();

  void installMod(final String moduleName);

  void uninstallMod(String moduleName);

  boolean pullInDependencies(String moduleName);

  void registerExitHandler(Handler<Void> handler);

  Vertx getVertx();

  // debug only
  int checkNoModules();

}
