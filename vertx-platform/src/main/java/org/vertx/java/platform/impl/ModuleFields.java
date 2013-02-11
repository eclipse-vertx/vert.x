package org.vertx.java.platform.impl;/*
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

import org.vertx.java.core.json.JsonObject;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ModuleFields {

  private final JsonObject conf;

  public ModuleFields(JsonObject conf) {
    this.conf = conf;
  }

  public String getMain() {
    return conf.getString("main");
  }

  public boolean isWorker() {
    return getBooleanField("worker");
  }

  public boolean isMultiThreaded() {
    return getBooleanField("multi-threaded");
  }

  public boolean isPreserveCurrentWorkingDirectory() {
    return getBooleanField("preserve-cwd");
  }

  public boolean isAutoRedeploy() {
    return getBooleanField("auto-redeploy");
  }

  public boolean isResident() {
    return getBooleanField("resident");
  }

  public boolean isSystem() {
    return getBooleanField("system");
  }

  public String getIncludes() {
    return conf.getString("includes");
  }

  public String getDescription() {
    return conf.getString("description");
  }

  public String getKeywords() {
    return conf.getString("keywords");
  }

  /*
  Comma separated list of modules that are deployed by this module
   */
  public String getDeploys() {
    return conf.getString("deploys");
  }

  public String getLicence() {
    return conf.getString("licence");
  }

  public String getProjectURL() {
    return conf.getString("project-url");
  }

  public String getAuthor() {
    return conf.getString("authors");
  }

  private boolean getBooleanField(String name) {
    Boolean res = conf.getBoolean(name);
    if (res == null) {
      res = Boolean.FALSE;
    }
    return res;
  }




}
