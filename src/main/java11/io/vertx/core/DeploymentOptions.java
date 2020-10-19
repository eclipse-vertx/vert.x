/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Options for configuring a verticle deployment.
 * <p>
 *
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject(generateConverter = true, publicConverter = false)
public class DeploymentOptions {

  public static final boolean DEFAULT_WORKER = false;
  public static final boolean DEFAULT_HA = false;
  public static final int DEFAULT_INSTANCES = 1;

  private JsonObject config;
  private boolean worker;
  private String workerPoolName;
  private int workerPoolSize;
  private long maxWorkerExecuteTime;
  private boolean ha;
  private int instances;
  private TimeUnit maxWorkerExecuteTimeUnit;
  private ClassLoader classLoader;

  /**
   * Default constructor
   */
  public DeploymentOptions() {
    this.worker = DEFAULT_WORKER;
    this.config = null;
    this.ha = DEFAULT_HA;
    this.instances = DEFAULT_INSTANCES;
    this.workerPoolName = null;
    this.workerPoolSize = VertxOptions.DEFAULT_WORKER_POOL_SIZE;
    this.maxWorkerExecuteTime = VertxOptions.DEFAULT_MAX_WORKER_EXECUTE_TIME;
    this.maxWorkerExecuteTimeUnit = VertxOptions.DEFAULT_MAX_WORKER_EXECUTE_TIME_UNIT;
  }

  /**
   * Copy constructor
   *
   * @param other the instance to copy
   */
  public DeploymentOptions(DeploymentOptions other) {
    this.config = other.getConfig() == null ? null : other.getConfig().copy();
    this.worker = other.isWorker();
    this.ha = other.isHa();
    this.instances = other.instances;
    this.workerPoolName = other.workerPoolName;
    setWorkerPoolSize(other.workerPoolSize);
    setMaxWorkerExecuteTime(other.maxWorkerExecuteTime);
    this.maxWorkerExecuteTimeUnit = other.maxWorkerExecuteTimeUnit;
  }

  /**
   * Constructor for creating a instance from JSON
   *
   * @param json  the JSON
   */
  public DeploymentOptions(JsonObject json) {
    this();
    DeploymentOptionsConverter.fromJson(json, this);
  }

  /**
   * Initialise the fields of this instance from the specified JSON
   *
   * @param json  the JSON
   */
  public void fromJson(JsonObject json) {
    this.config = json.getJsonObject("config");
    this.worker = json.getBoolean("worker", DEFAULT_WORKER);
    this.ha = json.getBoolean("ha", DEFAULT_HA);
    this.instances = json.getInteger("instances", DEFAULT_INSTANCES);
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

  /**
   * @return the worker pool name
   */
  public String getWorkerPoolName() {
    return workerPoolName;
  }

  /**
   * Set the worker pool name to use for this verticle. When no name is set, the Vert.x
   * worker pool will be used, when a name is set, the verticle will use a named worker pool.
   *
   * @param workerPoolName the worker pool name
   * @return a reference to this, so the API can be used fluently
   */
  public DeploymentOptions setWorkerPoolName(String workerPoolName) {
    this.workerPoolName = workerPoolName;
    return this;
  }

  /**
   * Get the maximum number of worker threads to be used by the worker pool when the verticle is deployed
   * with a {@link #setWorkerPoolName}. When the verticle does not use a named worker pool, this option
   * has no effect.
   * <p>
   * Worker threads are used for running blocking code and worker verticles.
   *
   * @return the maximum number of worker threads
   */
  public int getWorkerPoolSize() {
    return workerPoolSize;
  }

  /**
   * Set the maximum number of worker threads to be used by the Vert.x instance.
   *
   * @param workerPoolSize the number of threads
   * @return a reference to this, so the API can be used fluently
   */
  public DeploymentOptions setWorkerPoolSize(int workerPoolSize) {
    if (workerPoolSize < 1) {
      throw new IllegalArgumentException("workerPoolSize must be > 0");
    }
    this.workerPoolSize = workerPoolSize;
    return this;
  }

  /**
   * Get the value of max worker execute time, in {@link DeploymentOptions#setMaxWorkerExecuteTimeUnit maxWorkerExecuteTimeUnit}.
   * <p>
   * Vert.x will automatically log a warning if it detects that worker threads haven't returned within this time.
   * <p>
   * This can be used to detect where the user is blocking a worker thread for too long. Although worker threads
   * can be blocked longer than event loop threads, they shouldn't be blocked for long periods of time.
   *
   * @return The value of max worker execute time, the default value of {@link DeploymentOptions#setMaxWorkerExecuteTimeUnit} {@code maxWorkerExecuteTimeUnit} is {@link TimeUnit#NANOSECONDS}
   */
  public long getMaxWorkerExecuteTime() {
    return maxWorkerExecuteTime;
  }

  /**
   * Sets the value of max worker execute time, in {@link DeploymentOptions#setMaxWorkerExecuteTimeUnit maxWorkerExecuteTimeUnit}.
   * <p>
   * The default value of {@link DeploymentOptions#setMaxWorkerExecuteTimeUnit maxWorkerExecuteTimeUnit} is {@link TimeUnit#NANOSECONDS}
   *
   * @param maxWorkerExecuteTime the value of max worker execute time, in in {@link DeploymentOptions#setMaxWorkerExecuteTimeUnit maxWorkerExecuteTimeUnit}.
   * @return a reference to this, so the API can be used fluently
   */
  public DeploymentOptions setMaxWorkerExecuteTime(long maxWorkerExecuteTime) {
    if (maxWorkerExecuteTime < 1) {
      throw new IllegalArgumentException("maxWorkerExecuteTime must be > 0");
    }
    this.maxWorkerExecuteTime = maxWorkerExecuteTime;
    return this;
  }

  /**
   * @return the time unit of {@code maxWorkerExecuteTime}
   */
  public TimeUnit getMaxWorkerExecuteTimeUnit() {
    return maxWorkerExecuteTimeUnit;
  }

  /**
   * Set the time unit of {@code maxWorkerExecuteTime}
   * @param maxWorkerExecuteTimeUnit the time unit of {@code maxWorkerExecuteTime}
   * @return a reference to this, so the API can be used fluently
   */
  public DeploymentOptions setMaxWorkerExecuteTimeUnit(TimeUnit maxWorkerExecuteTimeUnit) {
    this.maxWorkerExecuteTimeUnit = maxWorkerExecuteTimeUnit;
    return this;
  }

  /**
   * @return the classloader used for deploying the Verticle
   */
  public ClassLoader getClassLoader() {
    return classLoader;
  }

  /**
   * Set the classloader to use for deploying the Verticle.
   *
   * <p> The {@code VerticleFactory} will use this classloader for creating the Verticle
   * and the Verticle {@link io.vertx.core.Context} will set this classloader as context
   * classloader for the tasks execution on context.
   *
   * <p> By default no classloader is required and the deployment will use the current thread context
   * classloader.
   *
   * @param classLoader the loader to use
   * @return a reference to this, so the API can be used fluently
   */
  public DeploymentOptions setClassLoader(ClassLoader classLoader) {
    this.classLoader = classLoader;
    return this;
  }
  /**
   * Does nothing.
   */
  public void checkIsolationNotDefined() {
  }

  /**
   * Convert this to JSON
   *
   * @return  the JSON
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    DeploymentOptionsConverter.toJson(this, json);
    return json;
  }
}
