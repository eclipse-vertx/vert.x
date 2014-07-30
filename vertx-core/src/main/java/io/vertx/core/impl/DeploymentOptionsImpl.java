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
import io.vertx.core.json.JsonObject;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DeploymentOptionsImpl implements DeploymentOptions {

  private JsonObject config;
  private boolean worker;
  private boolean multiThreaded;
  private String isolationGroup;
  private boolean ha;

  DeploymentOptionsImpl() {
  }

  DeploymentOptionsImpl(DeploymentOptions other) {
    this.config = other.getConfig() == null ? null : other.getConfig().copy();
    this.worker = other.isWorker();
    this.multiThreaded = other.isMultiThreaded();
    this.isolationGroup = other.getIsolationGroup();
    this.ha = other.isHA();
  }

  DeploymentOptionsImpl(JsonObject json) {
    this.config = json.getObject("config");
    this.worker = json.getBoolean("worker", false);
    this.multiThreaded = json.getBoolean("multiThreaded", false);
    this.isolationGroup = json.getString("isolationGroup", null);
    this.ha = json.getBoolean("ha", false);
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
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DeploymentOptions)) return false;

    DeploymentOptions that = (DeploymentOptions) o;

    if (multiThreaded != that.isMultiThreaded()) return false;
    if (worker != that.isWorker()) return false;
    if (config != null ? !config.equals(that.getConfig()) : that.getConfig() != null) return false;
    if (isolationGroup != null ? !isolationGroup.equals(that.getIsolationGroup()) : that.getIsolationGroup() != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = config != null ? config.hashCode() : 0;
    result = 31 * result + (worker ? 1 : 0);
    result = 31 * result + (multiThreaded ? 1 : 0);
    result = 31 * result + (isolationGroup != null ? isolationGroup.hashCode() : 0);
    return result;
  }

  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("worker: " + worker + "\n");
    builder.append("multiThreaded: " + multiThreaded + "\n");
    builder.append("isolationGroup: " + isolationGroup + "\n");
    builder.append("ha: " + ha + "\n");
    builder.append("config: " + config + "\n");
    return builder.toString();
  }


}
