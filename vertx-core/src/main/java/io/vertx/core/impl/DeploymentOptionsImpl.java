/*
 * Copyright 2014 Red Hat, Inc.
 *
 *   Red Hat licenses this file to you under the Apache License, version 2.0
 *   (the "License"); you may not use this file except in compliance with the
 *   License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *   License for the specific language governing permissions and limitations
 *   under the License.
 */

package io.vertx.core.impl;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DeploymentOptionsImpl implements DeploymentOptions {

  private JsonObject config;
  private boolean worker;
  private boolean multiThreaded;
  private String isolationGroup;
  private boolean ha;
  private List<String> extraClasspath;

  DeploymentOptionsImpl() {
  }

  DeploymentOptionsImpl(DeploymentOptions other) {
    this.config = other.getConfig() == null ? null : other.getConfig().copy();
    this.worker = other.isWorker();
    this.multiThreaded = other.isMultiThreaded();
    this.isolationGroup = other.getIsolationGroup();
    this.ha = other.isHA();
    this.extraClasspath = other.getExtraClasspath() == null ? null : new ArrayList<>(other.getExtraClasspath());
  }

  DeploymentOptionsImpl(JsonObject json) {
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

  @Override
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

  @Override
  public List<String> getExtraClasspath() {
    return extraClasspath;
  }

  @Override
  public DeploymentOptions setExtraClasspath(List<String> extraClasspath) {
    this.extraClasspath = extraClasspath;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DeploymentOptionsImpl that = (DeploymentOptionsImpl) o;

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
