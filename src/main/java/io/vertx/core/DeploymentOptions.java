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
import io.vertx.codegen.annotations.GenIgnore;
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
  private String isolationGroup;
  private String workerPoolName;
  private int workerPoolSize;
  private long maxWorkerExecuteTime;
  private boolean ha;
  private List<String> extraClasspath;
  private int instances;
  private List<String> isolatedClasses;
  private TimeUnit maxWorkerExecuteTimeUnit;
  private ClassLoader classLoader;

  /**
   * Default constructor
   */
  public DeploymentOptions() {
    this.worker = DEFAULT_WORKER;
    this.config = null;
    this.isolationGroup = null;
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
    this.isolationGroup = other.getIsolationGroup();
    this.ha = other.isHa();
    this.extraClasspath = other.getExtraClasspath() == null ? null : new ArrayList<>(other.getExtraClasspath());
    this.instances = other.instances;
    this.isolatedClasses = other.getIsolatedClasses() == null ? null : new ArrayList<>(other.getIsolatedClasses());
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
    this.isolationGroup = json.getString("isolationGroup");
    JsonArray arr = json.getJsonArray("extraClasspath");
    if (arr != null) {
      this.extraClasspath = arr.getList();
    }
    JsonArray arrIsolated = json.getJsonArray("isolatedClasses");
    if (arrIsolated != null) {
      this.isolatedClasses = arrIsolated.getList();
    }
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
   * Get the isolation group that will be used when deploying the verticle(s)
   * <br/>
   * <strong>IMPORTANT</strong> this feature is removed when running with Java 11 or above.
   *
   * @return the isolation group
   */
  @GenIgnore
  @Deprecated
  public String getIsolationGroup() {
    return isolationGroup;
  }

  /**
   * Set the isolation group that will be used when deploying the verticle(s)
   * <br/>
   * <strong>IMPORTANT</strong> this feature is removed when running with Java 11 or above.
   *
   * @param isolationGroup - the isolation group
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  @Deprecated
  public DeploymentOptions setIsolationGroup(String isolationGroup) {
    this.isolationGroup = isolationGroup;
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
   * Get any extra classpath to be used when deploying the verticle.
   * <p>
   * Ignored if no isolation group is set.
   * <br/>
   * <strong>IMPORTANT</strong> this feature is removed when running with Java 11 or above.
   *
   * @return  any extra classpath
   */
  @GenIgnore
  @Deprecated
  public List<String> getExtraClasspath() {
    return extraClasspath;
  }

  /**
   * Set any extra classpath to be used when deploying the verticle.
   * <p>
   * Ignored if no isolation group is set.
   * <br/>
   * <strong>IMPORTANT</strong> this feature is removed when running with Java 11 or above.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  @Deprecated
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

  /**
   * Get the list of isolated class names, the names can be a Java class fully qualified name such as
   * 'com.mycompany.myproject.engine.MyClass' or a wildcard matching such as `com.mycompany.myproject.*`.
   * <br/>
   * <strong>IMPORTANT</strong> this feature is removed when running with Java 11 or above.
   *
   * @return the list of isolated classes
   */
  @GenIgnore
  @Deprecated
  public List<String> getIsolatedClasses() {
    return isolatedClasses;
  }

  /**
   * Set the isolated class names.
   * <br/>
   * <strong>IMPORTANT</strong> this feature is removed when running with Java 11 or above.
   *
   * @param isolatedClasses the list of isolated class names
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  @Deprecated
  public DeploymentOptions setIsolatedClasses(List<String> isolatedClasses) {
    this.isolatedClasses = isolatedClasses;
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
   * Throw {@code IllegalArgumentException} when loader isolation configuration has been defined.
   */
  public void checkIsolationNotDefined() {
    if (getExtraClasspath() != null) {
      throw new IllegalArgumentException("Can't specify extraClasspath for already created verticle");
    }
    if (getIsolationGroup() != null) {
      throw new IllegalArgumentException("Can't specify isolationGroup for already created verticle");
    }
    if (getIsolatedClasses() != null) {
      throw new IllegalArgumentException("Can't specify isolatedClasses for already created verticle");
    }
  }

  /**
   * Convert this to JSON
   *
   * @return  the JSON
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    if (extraClasspath != null) {
      json.put("extraClasspath", new JsonArray(extraClasspath));
    }
    if (isolatedClasses != null) {
      json.put("isolatedClasses", new JsonArray(isolatedClasses));
    }
    if (isolationGroup != null) {
      json.put("isolationGroup", isolationGroup);
    }
    DeploymentOptionsConverter.toJson(this, json);
    return json;
  }
}
