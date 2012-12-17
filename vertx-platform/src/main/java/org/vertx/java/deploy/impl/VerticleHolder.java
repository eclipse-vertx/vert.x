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

package org.vertx.java.deploy.impl;

import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.DeploymentHandle;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.deploy.Verticle;
import org.vertx.java.deploy.VerticleFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class VerticleHolder implements DeploymentHandle {
  final Deployment deployment;
  final Context context;
  final Verticle verticle;
  final String loggerName;
  final Logger logger;
  //We put the config here too so it's still accessible to the verticle after it has been deployed
  //(deploy is async)
  final JsonObject config;
  final VerticleFactory factory;

  VerticleHolder(Deployment deployment, Context context, Verticle verticle, String loggerName,
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
    factory.reportException(t);
  }
}