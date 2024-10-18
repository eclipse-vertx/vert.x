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
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.file.FileSystemOptions;
import io.vertx.core.impl.cpu.CpuCoreSensor;
import io.vertx.core.json.JsonObject;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.tracing.TracingOptions;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Instances of this class are used to configure {@link io.vertx.core.Vertx} instances.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject
@JsonGen(publicConverter = false)
public class VertxOptions {

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

  /**
   * The default value of HA enabled = false
   */
  public static final boolean DEFAULT_HA_ENABLED = false;

  /**
   * The default value for preferring native transport = false
   */
  public static final boolean DEFAULT_PREFER_NATIVE_TRANSPORT = false;

  /**
   * The default value of warning exception time 5000000000 ns (5 seconds)
   * If a thread is blocked longer than this threshold, the warning log
   * contains a stack trace
   */
  private static final long DEFAULT_WARNING_EXCEPTION_TIME = TimeUnit.SECONDS.toNanos(5);

  /**
   * The default value of warning exception time unit = {@link TimeUnit#NANOSECONDS}
   */
  public static final TimeUnit DEFAULT_WARNING_EXCEPTION_TIME_UNIT = TimeUnit.NANOSECONDS;

  /**
   * The default value of thread context classloader disabling = {@code false}
   */
  public static final boolean DEFAULT_DISABLE_TCCL = false;

  /**
   * Set default value to false for aligning with the old behavior
   * By default, Vert.x threads are NOT daemons - we want them to prevent JVM exit so embedded user
   * doesn't have to explicitly prevent JVM from exiting.
   */
  public static final boolean DEFAULT_USE_DAEMON_THREAD = false;

  private int eventLoopPoolSize = DEFAULT_EVENT_LOOP_POOL_SIZE;
  private int workerPoolSize = DEFAULT_WORKER_POOL_SIZE;
  private int internalBlockingPoolSize = DEFAULT_INTERNAL_BLOCKING_POOL_SIZE;
  private long blockedThreadCheckInterval = DEFAULT_BLOCKED_THREAD_CHECK_INTERVAL;
  private long maxEventLoopExecuteTime = DEFAULT_MAX_EVENT_LOOP_EXECUTE_TIME;
  private long maxWorkerExecuteTime = DEFAULT_MAX_WORKER_EXECUTE_TIME;
  private boolean haEnabled = DEFAULT_HA_ENABLED;
  private int quorumSize = DEFAULT_QUORUM_SIZE;
  private String haGroup = DEFAULT_HA_GROUP;
  private MetricsOptions metricsOptions = new MetricsOptions();
  private TracingOptions tracingOptions;
  private FileSystemOptions fileSystemOptions = new FileSystemOptions();
  private long warningExceptionTime = DEFAULT_WARNING_EXCEPTION_TIME;
  private EventBusOptions eventBusOptions = new EventBusOptions();
  private AddressResolverOptions addressResolverOptions = new AddressResolverOptions();
  private boolean preferNativeTransport = DEFAULT_PREFER_NATIVE_TRANSPORT;
  private TimeUnit maxEventLoopExecuteTimeUnit = DEFAULT_MAX_EVENT_LOOP_EXECUTE_TIME_UNIT;
  private TimeUnit maxWorkerExecuteTimeUnit = DEFAULT_MAX_WORKER_EXECUTE_TIME_UNIT;
  private TimeUnit warningExceptionTimeUnit = DEFAULT_WARNING_EXCEPTION_TIME_UNIT;
  private TimeUnit blockedThreadCheckIntervalUnit = DEFAULT_BLOCKED_THREAD_CHECK_INTERVAL_UNIT;
  private boolean disableTCCL = DEFAULT_DISABLE_TCCL;
  private Boolean useDaemonThread = DEFAULT_USE_DAEMON_THREAD;

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
    this.blockedThreadCheckInterval = other.getBlockedThreadCheckInterval();
    this.maxEventLoopExecuteTime = other.getMaxEventLoopExecuteTime();
    this.maxWorkerExecuteTime = other.getMaxWorkerExecuteTime();
    this.internalBlockingPoolSize = other.getInternalBlockingPoolSize();
    this.haEnabled = other.isHAEnabled();
    this.quorumSize = other.getQuorumSize();
    this.haGroup = other.getHAGroup();
    this.metricsOptions = other.getMetricsOptions() != null ? new MetricsOptions(other.getMetricsOptions()) : null;
    this.fileSystemOptions = other.getFileSystemOptions() != null ? new FileSystemOptions(other.getFileSystemOptions()) : null;
    this.warningExceptionTime = other.warningExceptionTime;
    this.eventBusOptions = new EventBusOptions(other.eventBusOptions);
    this.addressResolverOptions = other.addressResolverOptions != null ? new AddressResolverOptions(other.getAddressResolverOptions()) : null;
    this.preferNativeTransport = other.preferNativeTransport;
    this.maxEventLoopExecuteTimeUnit = other.maxEventLoopExecuteTimeUnit;
    this.maxWorkerExecuteTimeUnit = other.maxWorkerExecuteTimeUnit;
    this.warningExceptionTimeUnit = other.warningExceptionTimeUnit;
    this.blockedThreadCheckIntervalUnit = other.blockedThreadCheckIntervalUnit;
    this.tracingOptions = other.tracingOptions != null ? other.tracingOptions.copy() : null;
    this.disableTCCL = other.disableTCCL;
    this.useDaemonThread = other.useDaemonThread;
  }

  /**
   * Create an instance from a {@link io.vertx.core.json.JsonObject}
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
   * Get the value of blocked thread check period, in {@link VertxOptions#setBlockedThreadCheckIntervalUnit blockedThreadCheckIntervalUnit}.
   * <p>
   * This setting determines how often Vert.x will check whether event loop threads are executing for too long.
   * <p>
   * The default value of {@link VertxOptions#setBlockedThreadCheckIntervalUnit blockedThreadCheckIntervalUnit} is {@link TimeUnit#MILLISECONDS}.
   *
   * @return the value of blocked thread check period, in {@link VertxOptions#setBlockedThreadCheckIntervalUnit blockedThreadCheckIntervalUnit}.
   */
  public long getBlockedThreadCheckInterval() {
    return blockedThreadCheckInterval;
  }

  /**
   * Sets the value of blocked thread check period, in {@link VertxOptions#setBlockedThreadCheckIntervalUnit blockedThreadCheckIntervalUnit}.
   * <p>
   * The default value of {@link VertxOptions#setBlockedThreadCheckIntervalUnit blockedThreadCheckIntervalUnit} is {@link TimeUnit#MILLISECONDS}
   *
   * @param blockedThreadCheckInterval the value of blocked thread check period, in {@link VertxOptions#setBlockedThreadCheckIntervalUnit blockedThreadCheckIntervalUnit}.
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setBlockedThreadCheckInterval(long blockedThreadCheckInterval) {
    if (blockedThreadCheckInterval < 1) {
      throw new IllegalArgumentException("blockedThreadCheckInterval must be > 0");
    }
    this.blockedThreadCheckInterval = blockedThreadCheckInterval;
    return this;
  }

  /**
   * Get the value of max event loop execute time, in {@link VertxOptions#setMaxEventLoopExecuteTimeUnit maxEventLoopExecuteTimeUnit}.
   * <p>
   * Vert.x will automatically log a warning if it detects that event loop threads haven't returned within this time.
   * <p>
   * This can be used to detect where the user is blocking an event loop thread, contrary to the Golden Rule of the
   * holy Event Loop.
   * <p>
   * The default value of {@link VertxOptions#setMaxEventLoopExecuteTimeUnit maxEventLoopExecuteTimeUnit} is {@link TimeUnit#NANOSECONDS}
   *
   * @return the value of max event loop execute time, in {@link VertxOptions#setMaxEventLoopExecuteTimeUnit maxEventLoopExecuteTimeUnit}.
   */
  public long getMaxEventLoopExecuteTime() {
    return maxEventLoopExecuteTime;
  }

  /**
   * Sets the value of max event loop execute time, in {@link VertxOptions#setMaxEventLoopExecuteTimeUnit maxEventLoopExecuteTimeUnit}.
   * <p>
   * The default value of {@link VertxOptions#setMaxEventLoopExecuteTimeUnit maxEventLoopExecuteTimeUnit}is {@link TimeUnit#NANOSECONDS}
   *
   * @param maxEventLoopExecuteTime the value of max event loop execute time, in {@link VertxOptions#setMaxEventLoopExecuteTimeUnit maxEventLoopExecuteTimeUnit}.
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setMaxEventLoopExecuteTime(long maxEventLoopExecuteTime) {
    if (maxEventLoopExecuteTime < 1) {
      throw new IllegalArgumentException("maxEventLoopExecuteTime must be > 0");
    }
    this.maxEventLoopExecuteTime = maxEventLoopExecuteTime;
    return this;
  }

  /**
   * Get the value of max worker execute time, in {@link VertxOptions#setMaxWorkerExecuteTimeUnit maxWorkerExecuteTimeUnit}.
   * <p>
   * Vert.x will automatically log a warning if it detects that worker threads haven't returned within this time.
   * <p>
   * This can be used to detect where the user is blocking a worker thread for too long. Although worker threads
   * can be blocked longer than event loop threads, they shouldn't be blocked for long periods of time.
   * <p>
   * The default value of {@link VertxOptions#setMaxWorkerExecuteTimeUnit maxWorkerExecuteTimeUnit} is {@link TimeUnit#NANOSECONDS}
   *
   * @return The value of max worker execute time, in {@link VertxOptions#setMaxWorkerExecuteTimeUnit maxWorkerExecuteTimeUnit}.
   */
  public long getMaxWorkerExecuteTime() {
    return maxWorkerExecuteTime;
  }

  /**
   * Sets the value of max worker execute time, in {@link VertxOptions#setMaxWorkerExecuteTimeUnit maxWorkerExecuteTimeUnit}.
   * <p>
   * The default value of {@link VertxOptions#setMaxWorkerExecuteTimeUnit maxWorkerExecuteTimeUnit} is {@link TimeUnit#NANOSECONDS}
   *
   * @param maxWorkerExecuteTime the value of max worker execute time, in {@link VertxOptions#setMaxWorkerExecuteTimeUnit maxWorkerExecuteTimeUnit}.
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setMaxWorkerExecuteTime(long maxWorkerExecuteTime) {
    if (maxWorkerExecuteTime < 1) {
      throw new IllegalArgumentException("maxWorkerpExecuteTime must be > 0");
    }
    this.maxWorkerExecuteTime = maxWorkerExecuteTime;
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
   * Will HA be enabled on the Vert.x instance?
   *
   * @return true if HA enabled, false otherwise
   */
  public boolean isHAEnabled() {
    return haEnabled;
  }

  /**
   * Set whether HA will be enabled on the Vert.x instance.
   *
   * @param haEnabled true if enabled, false if not.
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setHAEnabled(boolean haEnabled) {
    this.haEnabled = haEnabled;
    return this;
  }

  /**
   * Get the quorum size to be used when HA is enabled.
   *
   * @return the quorum size
   */
  public int getQuorumSize() {
    return quorumSize;
  }

  /**
   * Set the quorum size to be used when HA is enabled.
   *
   * @param quorumSize the quorum size
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setQuorumSize(int quorumSize) {
    if (quorumSize < 1) {
      throw new IllegalArgumentException("quorumSize should be >= 1");
    }
    this.quorumSize = quorumSize;
    return this;
  }

  /**
   * Get the HA group to be used when HA is enabled.
   *
   * @return the HA group
   */
  public String getHAGroup() {
    return haGroup;
  }

  /**
   * Set the HA group to be used when HA is enabled.
   *
   * @param haGroup the HA group to use
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setHAGroup(String haGroup) {
    Objects.requireNonNull(haGroup, "ha group cannot be null");
    this.haGroup = haGroup;
    return this;
  }

  /**
   * @return the metrics options
   */
  public MetricsOptions getMetricsOptions() {
    return metricsOptions;
  }

  /**
   * @return the file system options
   */
  public FileSystemOptions getFileSystemOptions() {
    return fileSystemOptions;
  }

  /**
   * Set the metrics options
   *
   * @param metrics the options
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setMetricsOptions(MetricsOptions metrics) {
    this.metricsOptions = metrics;
    return this;
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
   * Get the threshold value above this, the blocked warning contains a stack trace. in {@link VertxOptions#setWarningExceptionTimeUnit warningExceptionTimeUnit}.
   * <p>
   * The default value of {@link VertxOptions#setWarningExceptionTimeUnit warningExceptionTimeUnit} is {@link TimeUnit#NANOSECONDS}
   *
   * @return the warning exception time threshold
   */
  public long getWarningExceptionTime() {
    return warningExceptionTime;
  }

  /**
   * Set the threshold value above this, the blocked warning contains a stack trace. in {@link VertxOptions#setWarningExceptionTimeUnit warningExceptionTimeUnit}.
   * The default value of {@link VertxOptions#setWarningExceptionTimeUnit warningExceptionTimeUnit} is {@link TimeUnit#NANOSECONDS}
   *
   * @param warningExceptionTime
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setWarningExceptionTime(long warningExceptionTime) {
    if (warningExceptionTime < 1) {
      throw new IllegalArgumentException("warningExceptionTime must be > 0");
    }
    this.warningExceptionTime = warningExceptionTime;
    return this;
  }

  /**
   * @return the event bus option to configure the event bus communication (host, port, ssl...)
   */
  public EventBusOptions getEventBusOptions() {
    return eventBusOptions;
  }

  /**
   * Sets the event bus configuration to configure the host, port, ssl...
   *
   * @param options the event bus options
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setEventBusOptions(EventBusOptions options) {
    Objects.requireNonNull(options);
    this.eventBusOptions = options;
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
   * @return whether to prefer the native transport to the NIO transport
   */
  public boolean getPreferNativeTransport() {
    return preferNativeTransport;
  }

  /**
   * Set whether to prefer the native transport to the NIO transport.
   *
   * @param preferNativeTransport {@code true} to prefer the native transport
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setPreferNativeTransport(boolean preferNativeTransport) {
    this.preferNativeTransport = preferNativeTransport;
    return this;
  }

  /**
   * @return the time unit of {@code maxEventLoopExecuteTime}
   */
  public TimeUnit getMaxEventLoopExecuteTimeUnit() {
    return maxEventLoopExecuteTimeUnit;
  }

  /**
   * Set the time unit of {@code maxEventLoopExecuteTime}.
   *
   * @param maxEventLoopExecuteTimeUnit the time unit of {@code maxEventLoopExecuteTime}
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setMaxEventLoopExecuteTimeUnit(TimeUnit maxEventLoopExecuteTimeUnit) {
    this.maxEventLoopExecuteTimeUnit = maxEventLoopExecuteTimeUnit;
    return this;
  }

  /**
   * @return the time unit of {@code maxWorkerExecuteTime}
   */
  public TimeUnit getMaxWorkerExecuteTimeUnit() {
    return maxWorkerExecuteTimeUnit;
  }

  /**
   * Set the time unit of {@code maxWorkerExecuteTime}.
   *
   * @param maxWorkerExecuteTimeUnit the time unit of {@code maxWorkerExecuteTime}
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setMaxWorkerExecuteTimeUnit(TimeUnit maxWorkerExecuteTimeUnit) {
    this.maxWorkerExecuteTimeUnit = maxWorkerExecuteTimeUnit;
    return this;
  }

  /**
   * @return the time unit of {@code warningExceptionTime}
   */
  public TimeUnit getWarningExceptionTimeUnit() {
    return warningExceptionTimeUnit;
  }

  /**
   * Set the time unit of {@code warningExceptionTime}.
   *
   * @param warningExceptionTimeUnit the time unit of {@code warningExceptionTime}
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setWarningExceptionTimeUnit(TimeUnit warningExceptionTimeUnit) {
    this.warningExceptionTimeUnit = warningExceptionTimeUnit;
    return this;
  }

  /**
   * @return the time unit of {@code blockedThreadCheckInterval}
   */
  public TimeUnit getBlockedThreadCheckIntervalUnit() {
    return blockedThreadCheckIntervalUnit;
  }

  /**
   * Set the time unit of {@code blockedThreadCheckInterval}.
   *
   * @param blockedThreadCheckIntervalUnit the time unit of {@code warningExceptionTime}
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setBlockedThreadCheckIntervalUnit(TimeUnit blockedThreadCheckIntervalUnit) {
    this.blockedThreadCheckIntervalUnit = blockedThreadCheckIntervalUnit;
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

  /**
   * Returns whether we want to use daemon vertx thread.
   *
   * @return {@code true} means daemon, {@code false} means not daemon(user), {@code null} means do
   *         not change the daemon option of the created vertx thread.
   */
  public Boolean getUseDaemonThread() {
    return useDaemonThread;
  }

  /**
   * Mark the vertx thread as daemon thread or user thread.
   * <p/>
   * For keeping the old behavior, the default value is {@code false} instead of {@code null}.
   *
   * @param daemon {@code true} means daemon, {@code false} means not daemon(user), {@code null}
   *        means do not change the daemon option of the created vertx thread.
   */
  public VertxOptions setUseDaemonThread(Boolean daemon) {
    this.useDaemonThread = daemon;
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
        ", blockedThreadCheckIntervalUnit=" + blockedThreadCheckIntervalUnit +
        ", blockedThreadCheckInterval=" + blockedThreadCheckInterval +
        ", maxEventLoopExecuteTimeUnit=" + maxEventLoopExecuteTimeUnit +
        ", maxEventLoopExecuteTime=" + maxEventLoopExecuteTime +
        ", maxWorkerExecuteTimeUnit=" + maxWorkerExecuteTimeUnit +
        ", maxWorkerExecuteTime=" + maxWorkerExecuteTime +
        ", haEnabled=" + haEnabled +
        ", preferNativeTransport=" + preferNativeTransport +
        ", quorumSize=" + quorumSize +
        ", haGroup='" + haGroup + '\'' +
        ", metrics=" + metricsOptions +
        ", tracing=" + tracingOptions +
        ", fileSystemOptions=" + fileSystemOptions +
        ", addressResolver=" + addressResolverOptions.toJson() +
        ", eventbus=" + eventBusOptions.toJson() +
        ", warningExceptionTimeUnit=" + warningExceptionTimeUnit +
        ", warningExceptionTime=" + warningExceptionTime +
        ", disableTCCL=" + disableTCCL +
        ", useDaemonThread=" + useDaemonThread +
        '}';
  }
}
