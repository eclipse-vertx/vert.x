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

  public String getLangMod() {
    return conf.getString("lang-impl");
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

  public boolean isLoadFromModuleFirst() {
    return getBooleanField("load-from-module-first");
  }

  private boolean getBooleanField(String name) {
    Boolean res = conf.getBoolean(name);
    if (res == null) {
      res = Boolean.FALSE;
    }
    return res;
  }

}
