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

import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.DeploymentHandle;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Verticle;
import org.vertx.java.platform.VerticleFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class VerticleHolder implements DeploymentHandle {
  final Deployment deployment;
  final DefaultContext context;
  final Verticle verticle;
  final String loggerName;
  final Logger logger;
  //We put the config here too so it's still accessible to the verticle after it has been deployed
  //(deploy is async)
  final JsonObject config;
  final VerticleFactory factory;

  VerticleHolder(Deployment deployment, DefaultContext context, Verticle verticle, String loggerName,
                 Logger logger, JsonObject config,
                 VerticleFactory factory) {
    this.deployment = deployment;
    this.context = context;
    this.verticle = verticle;
    this.loggerName = loggerName;
    this.logger = logger;
    this.config = config;
    this.factory = factory;
  }

  public void reportException(Throwable t) {
    factory.reportException(logger, t);
  }
}