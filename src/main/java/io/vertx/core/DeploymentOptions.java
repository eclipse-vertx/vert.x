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

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Options for configuring a verticle deployment.
 * <p>
 *
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject
public class DeploymentOptions {

  public static final boolean DEFAULT_WORKER = false;
  public static final boolean DEFAULT_MULTI_THREADED = false;
  public static final String DEFAULT_ISOLATION_GROUP = null;
  public static final boolean DEFAULT_HA = false;
  public static final int DEFAULT_INSTANCES = 1;
  public static final boolean DEFAULT_REDEPLOY = false;
  public static final long DEFAULT_REDEPLOY_SCAN_PERIOD = 250;
  public static final long DEFAULT_REDEPLOY_GRACE_PERIOD = 1000;

  private JsonObject config;
  private boolean worker;
  private boolean multiThreaded;
  private String isolationGroup;
  private boolean ha;
  private List<String> extraClasspath;
  private int instances;
  private boolean redeploy;
  private long redeployScanPeriod;
  private long redeployGracePeriod;

  /**
   * Default constructor
   */
  public DeploymentOptions() {
    this.worker = DEFAULT_WORKER;
    this.config = null;
    this.multiThreaded = DEFAULT_MULTI_THREADED;
    this.isolationGroup = DEFAULT_ISOLATION_GROUP;
    this.ha = DEFAULT_HA;
    this.instances = DEFAULT_INSTANCES;
    this.redeploy = DEFAULT_REDEPLOY;
    this.redeployScanPeriod = DEFAULT_REDEPLOY_SCAN_PERIOD;
    this.redeployGracePeriod = DEFAULT_REDEPLOY_GRACE_PERIOD;
  }

  /**
   * Copy constructor
   *
   * @param other the instance to copy
   */
  public DeploymentOptions(DeploymentOptions other) {
    this.config = other.getConfig() == null ? null : other.getConfig().copy();
    this.worker = other.isWorker();
    this.multiThreaded = other.isMultiThreaded();
    this.isolationGroup = other.getIsolationGroup();
    this.ha = other.isHa();
    this.extraClasspath = other.getExtraClasspath() == null ? null : new ArrayList<>(other.getExtraClasspath());
    this.instances = other.instances;
    this.redeploy = other.redeploy;
    this.redeployScanPeriod = other.redeployScanPeriod;
    this.redeployGracePeriod = other.redeployGracePeriod;
  }

  /**
   * Constructor for creating a instance from JSON
   *
   * @param json  the JSON
   */
  public DeploymentOptions(JsonObject json) {
    fromJson(json);
  }

  /**
   * Initialise the fields of this instance from the specified JSON
   *
   * @param json  the JSON
   */
  public void fromJson(JsonObject json) {
    this.config = json.getJsonObject("config");
    this.worker = json.getBoolean("worker", DEFAULT_WORKER);
    this.multiThreaded = json.getBoolean("multiThreaded", DEFAULT_MULTI_THREADED);
    this.isolationGroup = json.getString("isolationGroup", DEFAULT_ISOLATION_GROUP);
    this.ha = json.getBoolean("ha", DEFAULT_HA);
    JsonArray arr = json.getJsonArray("extraClasspath", null);
    if (arr != null) {
      this.extraClasspath = arr.getList();
    }
    this.instances = json.getInteger("instances", DEFAULT_INSTANCES);
    this.redeploy = json.getBoolean("redeploy", DEFAULT_REDEPLOY);
    this.redeployScanPeriod = json.getLong("redeployScanPeriod", DEFAULT_REDEPLOY_SCAN_PERIOD);
    this.redeployGracePeriod = json.getLong("redeployGracePeriod", DEFAULT_REDEPLOY_GRACE_PERIOD);
  }

  /**
   * Get the JSON configuration that will be passed to the verticle(s) when deployed.
   *
   * @return  the JSON config
   */
  public JsonObject getConfig() {
    return config;
  }

  /**
   * Set the JSON configuration that will be passed to the verticle(s) when it's deployed
   *
   * @param config  the JSON config
   * @return a reference to this, so the API can be used fluently
   */
  public DeploymentOptions setConfig(JsonObject config) {
    this.config = config;
    return this;
  }

  /**
   * Should the verticle(s) be deployed as a worker verticle?
   *
   * @return true if will be deployed as worker, false otherwise
   */
  public boolean isWorker() {
    return worker;
  }

  /**
   * Set whether the verticle(s) should be deployed as a worker verticle
   *
   * @param worker true for worker, false otherwise
   * @return a reference to this, so the API can be used fluently
   */
  public DeploymentOptions setWorker(boolean worker) {
    this.worker = worker;
    return this;
  }

  /**
   * Should the verticle(s) be deployed as a multi-threaded worker verticle?
   * <p>
   * Ignored if {@link #isWorker} is not true.
   *
   * @return true if will be deployed as multi-threaded worker, false otherwise
   */
  public boolean isMultiThreaded() {
    return multiThreaded;
  }

  /**
   * Set whether the verticle(s) should be deployed as a multi-threaded worker verticle
   *
   * @param multiThreaded true for multi-threaded worker, false otherwise
   * @return a reference to this, so the API can be used fluently
   */
  public DeploymentOptions setMultiThreaded(boolean multiThreaded) {
    this.multiThreaded = multiThreaded;
    return this;
  }

  /**
   * Get the isolation group that will be used when deploying the verticle(s)
   *
   * @return the isolation group
   */
  public String getIsolationGroup() {
    return isolationGroup;
  }

  /**
   * Set the isolation group that will be used when deploying the verticle(s)
   *
   * @param isolationGroup - the isolation group
   * @return a reference to this, so the API can be used fluently
   */
  public DeploymentOptions setIsolationGroup(String isolationGroup) {
    this.isolationGroup = isolationGroup;
    return this;
  }

  /**
   * Convert this to JSON
   *
   * @return  the JSON
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    if (worker) json.put("worker", true);
    if (multiThreaded) json.put("multiThreaded", true);
    if (isolationGroup != null) json.put("isolationGroup", isolationGroup);
    if (ha) json.put("ha", true);
    if (config != null) json.put("config", config);
    if (extraClasspath != null) json.put("extraClasspath", new JsonArray(extraClasspath));
    if (instances != DEFAULT_INSTANCES) {
      json.put("instances", instances);
    }
    if (redeploy != DEFAULT_REDEPLOY) {
      json.put("redeploy", redeploy);
    }
    if (redeployScanPeriod != DEFAULT_REDEPLOY_SCAN_PERIOD) {
      json.put("redeployScanPeriod", redeployScanPeriod);
    }
    if (redeployGracePeriod != DEFAULT_REDEPLOY_GRACE_PERIOD) {
      json.put("redeployGracePeriod", redeployGracePeriod);
    }
    return json;
  }

  /**
   * Will the verticle(s) be deployed as HA (highly available) ?
   *
   * @return true if HA, false otherwise
   */
  public boolean isHa() {
    return ha;
  }

  /**
   * Set whether the verticle(s) will be deployed as HA.
   *
   * @param ha  true if to be deployed as HA, false otherwise
   * @return a reference to this, so the API can be used fluently
   */
  public DeploymentOptions setHa(boolean ha) {
    this.ha = ha;
    return this;
  }

  /**
   * Get any extra classpath to be used when deploying the verticle.
   * <p>
   * Ignored if no isolation group is set.
   *
   * @return  any extra classpath
   */
  public List<String> getExtraClasspath() {
    return extraClasspath;
  }

  /**
   * Set any extra classpath to be used when deploying the verticle.
   * <p>
   * Ignored if no isolation group is set.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public DeploymentOptions setExtraClasspath(List<String> extraClasspath) {
    this.extraClasspath = extraClasspath;
    return this;
  }

  /**
   * Get the number of instances that should be deployed.
   *
   * @return  the number of instances
   */
  public int getInstances() {
    return instances;
  }

  /**
   * Set the number of instances that should be deployed.
   *
   * @param instances  the number of instances
   * @return a reference to this, so the API can be used fluently
   */
  public DeploymentOptions setInstances(int instances) {
    this.instances = instances;
    return this;
  }

  public boolean isRedeploy() {
    return redeploy;
  }

  public DeploymentOptions setRedeploy(boolean redeploy) {
    this.redeploy = redeploy;
    return this;
  }

  public long getRedeployScanPeriod() {
    return redeployScanPeriod;
  }

  public DeploymentOptions setRedeployScanPeriod(long redeployScanPeriod) {
    if (redeployScanPeriod < 1) {
      throw new IllegalArgumentException("redeployScanPeriod must be > 0");
    }
    this.redeployScanPeriod = redeployScanPeriod;
    return this;
  }

  public long getRedeployGracePeriod() {
    return redeployGracePeriod;
  }

  public DeploymentOptions setRedeployGracePeriod(long redeployGracePeriod) {
    if (redeployGracePeriod < 1) {
      throw new IllegalArgumentException("redeployGracePeriod must be > 0");
    }
    this.redeployGracePeriod = redeployGracePeriod;
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
    if (instances != that.instances) return false;
    if (redeploy != that.redeploy) return false;
    if (redeployScanPeriod != that.redeployScanPeriod) return false;
    if (redeployGracePeriod != that.redeployGracePeriod) return false;

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
    result = 31 * result + instances;
    result = 31 * result + (redeploy ? 1 : 0);
    result = 31 * result + (int) (redeployScanPeriod ^ (redeployScanPeriod >>> 32));
    result = 31 * result + (int) (redeployGracePeriod ^ (redeployGracePeriod >>> 32));
    return result;
  }
}
