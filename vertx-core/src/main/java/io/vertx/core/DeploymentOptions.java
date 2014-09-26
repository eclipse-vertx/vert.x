/*
 * Copyright (c) 2011-2014 The original author or authors
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

package io.vertx.core;

import io.vertx.codegen.annotations.Options;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@Options
public class DeploymentOptions {

  private JsonObject config;
  private boolean worker;
  private boolean multiThreaded;
  private String isolationGroup;
  private boolean ha;
  private List<String> extraClasspath;

  public DeploymentOptions() {
  }

  public DeploymentOptions(DeploymentOptions other) {
    this.config = other.getConfig() == null ? null : other.getConfig().copy();
    this.worker = other.isWorker();
    this.multiThreaded = other.isMultiThreaded();
    this.isolationGroup = other.getIsolationGroup();
    this.ha = other.isHA();
    this.extraClasspath = other.getExtraClasspath() == null ? null : new ArrayList<>(other.getExtraClasspath());
  }

  public DeploymentOptions(JsonObject json) {
    this.config = json.getObject("config");
    this.worker = json.getBoolean("worker", false);
    this.multiThreaded = json.getBoolean("multiThreaded", false);
    this.isolationGroup = json.getString("isolationGroup", null);
    this.ha = json.getBoolean("ha", false);
    JsonArray arr = json.getArray("extraClasspath", null);
    if (arr != null) {
      this.extraClasspath = arr.toList();
    }
  }

  public JsonObject getConfig() {
    return config;
  }

  public DeploymentOptions setConfig(JsonObject config) {
    this.config = config;
    return this;
  }

  public boolean isWorker() {
    return worker;
  }

  public DeploymentOptions setWorker(boolean worker) {
    this.worker = worker;
    return this;
  }

  public boolean isMultiThreaded() {
    return multiThreaded;
  }

  public DeploymentOptions setMultiThreaded(boolean multiThreaded) {
    this.multiThreaded = multiThreaded;
    return this;
  }

  public String getIsolationGroup() {
    return isolationGroup;
  }

  public DeploymentOptions setIsolationGroup(String isolationGroup) {
    this.isolationGroup = isolationGroup;
    return this;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    if (worker) json.putBoolean("worker", true);
    if (multiThreaded) json.putBoolean("multiThreaded", true);
    if (isolationGroup != null) json.putString("isolationGroup", isolationGroup);
    if (ha) json.putBoolean("ha", true);
    if (config != null) json.putObject("config", config);
    if (extraClasspath != null) json.putArray("extraClasspath", new JsonArray(extraClasspath));
    return json;
  }

  public boolean isHA() {
    return ha;
  }

  public DeploymentOptions setHA(boolean ha) {
    this.ha = ha;
    return this;
  }

  public List<String> getExtraClasspath() {
    return extraClasspath;
  }

  public DeploymentOptions setExtraClasspath(List<String> extraClasspath) {
    this.extraClasspath = extraClasspath;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DeploymentOptions that = (DeploymentOptions) o;

    if (ha != that.ha) return false;
    if (multiThreaded != that.multiThreaded) return false;
    if (worker != that.worker) return false;
    if (config != null ? !config.equals(that.config) : that.config != null) return false;
    if (extraClasspath != null ? !extraClasspath.equals(that.extraClasspath) : that.extraClasspath != null)
      return false;
    if (isolationGroup != null ? !isolationGroup.equals(that.isolationGroup) : that.isolationGroup != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = config != null ? config.hashCode() : 0;
    result = 31 * result + (worker ? 1 : 0);
    result = 31 * result + (multiThreaded ? 1 : 0);
    result = 31 * result + (isolationGroup != null ? isolationGroup.hashCode() : 0);
    result = 31 * result + (ha ? 1 : 0);
    result = 31 * result + (extraClasspath != null ? extraClasspath.hashCode() : 0);
    return result;
  }
}
