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

package org.vertx.java.deploy.impl.cli;

import org.vertx.java.core.json.JsonObject;
import org.vertx.java.deploy.impl.VerticleManager;

import java.net.URL;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DeployCommand extends VertxCommand {

  public boolean worker;
  public String name;
  public String main;
  public String conf;
  public URL[] urls;
  public int instances;

  public DeployCommand(boolean worker, String name, String main, String conf, URL[] urls, int instances) {
    this.worker = worker;
    this.name = name;
    this.conf = conf;
    this.main = main;
    this.urls = urls;
    this.instances = instances;
  }

  public DeployCommand() {
  }

  public String execute(VerticleManager appMgr) throws Exception {
    JsonObject jsonConf;
    if (conf != null) {
      jsonConf = new JsonObject(conf);
    } else {
      jsonConf = null;
    }
    String appName = appMgr.deploy(worker, name, main, jsonConf, urls, instances, null, null);
    return "Deployment: " + appName;
  }
}
