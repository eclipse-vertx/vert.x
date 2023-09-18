/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
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
import io.vertx.core.json.JsonObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Worker pool options.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject(generateConverter = true, publicConverter = false)
public class WorkerPoolOptions implements WorkerOptions {

  private String name;
  private int size;
  private long maxExecuteTime;
  private TimeUnit maxExecuteTimeUnit;

  /**
   * Default constructor
   */
  public WorkerPoolOptions() {
    this.size = VertxOptions.DEFAULT_WORKER_POOL_SIZE;
    this.maxExecuteTime = VertxOptions.DEFAULT_MAX_WORKER_EXECUTE_TIME;
    this.maxExecuteTimeUnit = VertxOptions.DEFAULT_MAX_WORKER_EXECUTE_TIME_UNIT;
  }

  public WorkerPoolOptions(JsonObject json) {
    WorkerPoolOptionsConverter.fromJson(json, this);
  }

  /**
   * Copy constructor
   */
  public WorkerPoolOptions(WorkerPoolOptions other) {
    this.name = other.name;
    this.size = other.size;
    this.maxExecuteTime = other.maxExecuteTime;
    this.maxExecuteTimeUnit = other.maxExecuteTimeUnit;
  }

  @Override
  public ExecutorService createExecutor(Vertx vertx) {
    throw new UnsupportedOperationException();
  }

  @Override
  public WorkerPoolOptions copy() {
    return new WorkerPoolOptions(this);
  }

  /**
   * @return the worker pool name
   */
  public String getName() {
    return name;
  }

  /**
   * Set the worker pool name to use.
   *
   * @param name the worker pool name
   * @return a reference to this, so the API can be used fluently
   */
  public WorkerPoolOptions setName(String name) {
    this.name = name;
    return this;
  }

  /**
   * Get the maximum number of worker threads to be used by the worker pool.
   *
   * @return the maximum number of worker threads
   */
  public int getSize() {
    return size;
  }

  /**
   * Set the maximum number of worker threads to be used by the pool.
   *
   * @param size the number of threads
   * @return a reference to this, so the API can be used fluently
   */
  public WorkerPoolOptions setSize(int size) {
    if (size < 1) {
      throw new IllegalArgumentException("size must be > 0");
    }
    this.size = size;
    return this;
  }

  /**
   * Get the value of max worker execute time, in {@link #setMaxExecuteTimeUnit maxWorkerExecuteTimeUnit}.
   * <p>
   * Vert.x will automatically log a warning if it detects that worker threads haven't returned within this time.
   * <p>
   * This can be used to detect where the user is blocking a worker thread for too long. Although worker threads
   * can be blocked longer than event loop threads, they shouldn't be blocked for long periods of time.
   *
   * @return The value of max execute time, the default value of {@link #setMaxExecuteTimeUnit} is {@link TimeUnit#NANOSECONDS}
   */
  public long getMaxExecuteTime() {
    return maxExecuteTime;
  }

  /**
   * Sets the value of max worker execute time, in {@link DeploymentOptions#setMaxWorkerExecuteTimeUnit maxWorkerExecuteTimeUnit}.
   * <p>
   * The default value of {@link DeploymentOptions#setMaxWorkerExecuteTimeUnit maxWorkerExecuteTimeUnit} is {@link TimeUnit#NANOSECONDS}
   *
   * @param maxExecuteTime the value of max worker execute time, in {@link DeploymentOptions#setMaxWorkerExecuteTimeUnit maxWorkerExecuteTimeUnit}.
   * @return a reference to this, so the API can be used fluently
   */
  public WorkerPoolOptions setMaxExecuteTime(long maxExecuteTime) {
    if (maxExecuteTime < 1) {
      throw new IllegalArgumentException("maxExecuteTime must be > 0");
    }
    this.maxExecuteTime = maxExecuteTime;
    return this;
  }

  /**
   * @return the time unit of {@code maxExecuteTime}
   */
  public TimeUnit getMaxExecuteTimeUnit() {
    return maxExecuteTimeUnit;
  }

  /**
   * Set the time unit of {@link #maxExecuteTime}
   *
   * @param maxExecuteTimeUnit the time unit of {@link #maxExecuteTime}
   * @return a reference to this, so the API can be used fluently
   */
  public WorkerPoolOptions setMaxExecuteTimeUnit(TimeUnit maxExecuteTimeUnit) {
    this.maxExecuteTimeUnit = maxExecuteTimeUnit;
    return this;
  }

  /**
   * Convert this to JSON
   *
   * @return  the JSON
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    WorkerPoolOptionsConverter.toJson(this, json);
    return json;
  }
}
