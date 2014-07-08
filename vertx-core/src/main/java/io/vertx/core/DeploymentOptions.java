/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core;

import io.vertx.core.gen.Options;
import io.vertx.core.json.JsonObject;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@Options
public class DeploymentOptions {

  private JsonObject config;
  private boolean worker;
  private boolean multiThreaded;
  private String isolationGroup;

  public DeploymentOptions() {
  }

  public DeploymentOptions(DeploymentOptions other) {
    this.config = other.config;
    this.worker = other.worker;
    this.multiThreaded = other.multiThreaded;
    this.isolationGroup = other.isolationGroup;
  }

  public DeploymentOptions(JsonObject json) {
    this.config = json.getObject("config");
    this.worker = json.getBoolean("worker", false);
    this.multiThreaded = json.getBoolean("multiThreaded", false);
    this.isolationGroup = json.getString("isolationGroup", null);
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
}
