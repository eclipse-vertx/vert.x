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

import org.vertx.java.core.json.JsonObject;
import org.vertx.java.deploy.VerticleFactory;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Deployment {
  public final String name;
  public final String modName;
  public final int instances;
  public final JsonObject config;
  public final URL[] urls;
  public final File modDir;
  public final List<VerticleHolder> verticles = new ArrayList<>();
  public final List<String> childDeployments = new ArrayList<>();
  public final String parentDeploymentName;
  public final boolean autoRedeploy;

  public Deployment(String name, String modName, int instances, JsonObject config,
             URL[] urls, File modDir, String parentDeploymentName,
             boolean autoRedeploy) {
    this.name = name;
    this.modName = modName;
    this.instances = instances;
    this.config = config;
    this.urls = urls;
    this.modDir = modDir;
    this.parentDeploymentName = parentDeploymentName;
    this.autoRedeploy = autoRedeploy;
  }
}