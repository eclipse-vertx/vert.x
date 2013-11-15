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
import org.vertx.java.platform.PlatformManager;

import java.util.Map;

/**
 * Internal interface - not designed to be publicly used
 */
public interface PlatformManagerInternal extends PlatformManager {

  JsonObject config();

  Logger logger();

  void removeModule(String moduleKey);

  void exit();

  int checkNoModules();

  Map<String, Deployment> deployments();

  void deployModuleInternal(String moduleName, JsonObject config,
                            int instances, boolean ha, Handler<AsyncResult<String>> doneHandler);
}
