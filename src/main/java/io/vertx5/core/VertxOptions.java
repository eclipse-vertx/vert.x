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

package io.vertx5.core;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.file.FileSystemOptions;
import io.vertx.core.impl.cpu.CpuCoreSensor;
import io.vertx.core.json.JsonObject;
import io.vertx.core.tracing.TracingOptions;

import java.util.concurrent.TimeUnit;

/**
 * Instances of this class are used to configure {@link Vertx} instances.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject(generateConverter = true, publicConverter = false)
public class VertxOptions {

  private static final String DISABLE_TCCL_PROP_NAME = "vertx.disableTCCL";

  /**
   * The default number of event loop threads to be used  = 2 * number of cores on the machine
   */
  public static final int DEFAULT_EVENT_LOOP_POOL_SIZE = 2 * CpuCoreSensor.availableProcessors();

  /**
   * The default number of threads in the worker pool = 20
   */
  public static final int DEFAULT_WORKER_POOL_SIZE = 20;

  /**
   * The default number of threads in the internal blocking  pool (used by some internal operations) = 20
   */
  public static final int DEFAULT_INTERNAL_BLOCKING_POOL_SIZE = 20;

  /**
   * The default value of blocked thread check interval = 1000 ms.
   */
  public static final long DEFAULT_BLOCKED_THREAD_CHECK_INTERVAL = TimeUnit.SECONDS.toMillis(1);

  /**
   * The default value of blocked thread check interval unit = {@link TimeUnit#MILLISECONDS}
   */
  public static final TimeUnit DEFAULT_BLOCKED_THREAD_CHECK_INTERVAL_UNIT = TimeUnit.MILLISECONDS;

  /**
   * The default value of max event loop execute time = 2000000000 ns (2 seconds)
   */
  public static final long DEFAULT_MAX_EVENT_LOOP_EXECUTE_TIME = TimeUnit.SECONDS.toNanos(2);

  /**
   * The default value of max event loop execute time unit = {@link TimeUnit#NANOSECONDS}
   */
  public static final TimeUnit DEFAULT_MAX_EVENT_LOOP_EXECUTE_TIME_UNIT = TimeUnit.NANOSECONDS;

  /**
   * The default value of max worker execute time = 60000000000 ns (60 seconds)
   */
  public static final long DEFAULT_MAX_WORKER_EXECUTE_TIME = TimeUnit.SECONDS.toNanos(60);

  /**
   * The default value of max worker execute time unit = {@link TimeUnit#NANOSECONDS}
   */
  public static final TimeUnit DEFAULT_MAX_WORKER_EXECUTE_TIME_UNIT = TimeUnit.NANOSECONDS;

  /**
   * The default value of quorum size = 1
   */
  public static final int DEFAULT_QUORUM_SIZE = 1;

  /**
   * The default value of Ha group is "__DEFAULT__"
   */
  public static final String DEFAULT_HA_GROUP = "__DEFAULT__";

  public static final boolean DEFAULT_DISABLE_TCCL = Boolean.getBoolean(DISABLE_TCCL_PROP_NAME);

  private int eventLoopPoolSize = DEFAULT_EVENT_LOOP_POOL_SIZE;
  private int workerPoolSize = DEFAULT_WORKER_POOL_SIZE;
  private int internalBlockingPoolSize = DEFAULT_INTERNAL_BLOCKING_POOL_SIZE;
  private TracingOptions tracingOptions;
  private FileSystemOptions fileSystemOptions = new FileSystemOptions();
  private AddressResolverOptions addressResolverOptions = new AddressResolverOptions();
  private boolean disableTCCL = DEFAULT_DISABLE_TCCL;

  /**
   * Default constructor
   */
  public VertxOptions() {
  }

  /**
   * Copy constructor
   *
   * @param other The other {@code VertxOptions} to copy when creating this
   */
  public VertxOptions(VertxOptions other) {
    this.eventLoopPoolSize = other.getEventLoopPoolSize();
    this.workerPoolSize = other.getWorkerPoolSize();
    this.internalBlockingPoolSize = other.getInternalBlockingPoolSize();
    this.fileSystemOptions = other.getFileSystemOptions() != null ? new FileSystemOptions(other.getFileSystemOptions()) : null;
    this.addressResolverOptions = other.addressResolverOptions != null ? new AddressResolverOptions(other.getAddressResolverOptions()) : null;
    this.tracingOptions = other.tracingOptions != null ? other.tracingOptions.copy() : null;
    this.disableTCCL = other.disableTCCL;
  }

  /**
   * Create an instance from a {@link JsonObject}
   *
   * @param json the JsonObject to create it from
   */
  public VertxOptions(JsonObject json) {
    this();
    VertxOptionsConverter.fromJson(json, this);
  }

  /**
   * Get the number of event loop threads to be used by the Vert.x instance.
   *
   * @return the number of threads
   */
  public int getEventLoopPoolSize() {
    return eventLoopPoolSize;
  }

  /**
   * Set the number of event loop threads to be used by the Vert.x instance.
   *
   * @param eventLoopPoolSize the number of threads
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setEventLoopPoolSize(int eventLoopPoolSize) {
    if (eventLoopPoolSize < 1) {
      throw new IllegalArgumentException("eventLoopPoolSize must be > 0");
    }
    this.eventLoopPoolSize = eventLoopPoolSize;
    return this;
  }

  /**
   * Get the maximum number of worker threads to be used by the Vert.x instance.
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
  public VertxOptions setWorkerPoolSize(int workerPoolSize) {
    if (workerPoolSize < 1) {
      throw new IllegalArgumentException("workerPoolSize must be > 0");
    }
    this.workerPoolSize = workerPoolSize;
    return this;
  }

  /**
   * Get the value of internal blocking pool size.
   * <p>
   * Vert.x maintains a pool for internal blocking operations
   *
   * @return the value of internal blocking pool size
   */
  public int getInternalBlockingPoolSize() {
    return internalBlockingPoolSize;
  }

  /**
   * Set the value of internal blocking pool size
   *
   * @param internalBlockingPoolSize the maximumn number of threads in the internal blocking pool
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setInternalBlockingPoolSize(int internalBlockingPoolSize) {
    if (internalBlockingPoolSize < 1) {
      throw new IllegalArgumentException("internalBlockingPoolSize must be > 0");
    }
    this.internalBlockingPoolSize = internalBlockingPoolSize;
    return this;
  }

  /**
   * @return the file system options
   */
  public FileSystemOptions getFileSystemOptions() {
    return fileSystemOptions;
  }

  /**
   * Set the file system options
   *
   * @param fileSystemOptions the options
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setFileSystemOptions(FileSystemOptions fileSystemOptions) {
    this.fileSystemOptions = fileSystemOptions;
    return this;
  }

  /**
   * @return the address resolver options to configure resolving DNS servers, cache TTL, etc...
   */
  public AddressResolverOptions getAddressResolverOptions() {
    return addressResolverOptions;
  }

  /**
   * Sets the address resolver configuration to configure resolving DNS servers, cache TTL, etc...
   *
   * @param addressResolverOptions the address resolver options
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setAddressResolverOptions(AddressResolverOptions addressResolverOptions) {
    this.addressResolverOptions = addressResolverOptions;
    return this;
  }

  /**
   * If tracing is disabled, the value will be {@code null}.
   *
   * @return the tracing options
   */
  public TracingOptions getTracingOptions() {
    return tracingOptions;
  }

  /**
   * Set the tracing options
   *
   * @param tracingOptions the options
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setTracingOptions(TracingOptions tracingOptions) {
    this.tracingOptions = tracingOptions;
    return this;
  }

  /**
   * @return whether Vert.x sets the {@link Context} classloader as the thread context classloader on actions executed on that {@link Context}
   */
  public boolean getDisableTCCL() {
    return disableTCCL;
  }

  /**
   * Configures whether Vert.x sets the {@link Context} classloader as the thread context classloader on actions executed on that {@link Context}.
   *
   * When a {@link Context} is created the current thread classloader is captured and associated with this classloader.
   *
   * Likewise when a Verticle is created, the Verticle's {@link Context} classloader is set to the current thread classloader
   * unless this classloader is overriden by {@link DeploymentOptions#getClassLoader()}.
   *
   * This setting overrides the (legacy) system property {@code vertx.disableTCCL} and provides control at the
   * Vertx instance level.
   *
   * @param disableTCCL {@code true} to disable thread context classloader update by Vertx
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setDisableTCCL(boolean disableTCCL) {
    this.disableTCCL = disableTCCL;
    return this;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    VertxOptionsConverter.toJson(this, json);
    return json;
  }

  @Override
  public String toString() {
    return "VertxOptions{" +
        "eventLoopPoolSize=" + eventLoopPoolSize +
        ", workerPoolSize=" + workerPoolSize +
        ", internalBlockingPoolSize=" + internalBlockingPoolSize +
        ", tracing=" + tracingOptions +
        ", fileSystemOptions=" + fileSystemOptions +
        ", addressResolver=" + addressResolverOptions.toJson() +
        ", disableTCCL=" + disableTCCL +
        '}';
  }
}
