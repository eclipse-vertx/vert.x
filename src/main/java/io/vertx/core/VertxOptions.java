/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
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
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.file.FileSystemOptions;
import io.vertx.core.impl.cpu.CpuCoreSensor;
import io.vertx.core.json.JsonObject;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.tracing.TracingOptions;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static io.vertx.core.file.FileSystemOptions.DEFAULT_FILE_CACHING_ENABLED;

/**
 * Instances of this class are used to configure {@link io.vertx.core.Vertx} instances.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject(generateConverter = true, publicConverter = false)
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
   * The default value of whether Vert.x is clustered = false.
   */
  public static final boolean DEFAULT_CLUSTERED = false;

  /**
   * The default hostname to use when clustering = "localhost"
   */
  public static final String DEFAULT_CLUSTER_HOST = "localhost";

  /**
   * The default port to use when clustering = 0 (meaning assign a random port)
   */
  public static final int DEFAULT_CLUSTER_PORT = 0;

  /**
   * The default cluster public host to use = null which means use the same as the cluster host
   */
  public static final String DEFAULT_CLUSTER_PUBLIC_HOST = null;

  /**
   * The default cluster public port to use = -1 which means use the same as the cluster port
   */
  public static final int DEFAULT_CLUSTER_PUBLIC_PORT = -1;

  /**
   * The default value of cluster ping interval = 20000 ms.
   */
  public static final long DEFAULT_CLUSTER_PING_INTERVAL = 20000;

  /**
   * The default value of cluster ping reply interval = 20000 ms.
   */
  public static final long DEFAULT_CLUSTER_PING_REPLY_INTERVAL = 20000;

  /**
   * The default value of blocked thread check interval = 1000 ms.
   */
  public static final long DEFAULT_BLOCKED_THREAD_CHECK_INTERVAL = 1000;

  /**
   * The default value of blocked thread check interval unit = TimeUnit.NANOSECONDS
   */
  public static final TimeUnit DEFAULT_BLOCKED_THREAD_CHECK_INTERVAL_UNIT = TimeUnit.MILLISECONDS;

  /**
   * The default value of max event loop execute time = 2000000000 ns (2 seconds)
   */
  public static final long DEFAULT_MAX_EVENT_LOOP_EXECUTE_TIME = 2L * 1000 * 1000000;

  /**
   * The default value of max event loop execute time unit = TimeUnit.NANOSECONDS
   */
  public static final TimeUnit DEFAULT_MAX_EVENT_LOOP_EXECUTE_TIME_UNIT = TimeUnit.NANOSECONDS;

  /**
   * The default value of max worker execute time = 60000000000 ns (60 seconds)
   */
  public static final long DEFAULT_MAX_WORKER_EXECUTE_TIME = 60L * 1000 * 1000000;

  /**
   * The default value of max worker execute time unit = TimeUnit.NANOSECONDS
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
   * The default value of warning exception time unit = TimeUnit.NANOSECONDS
   */
  public static final TimeUnit DEFAULT_WARNING_EXCEPTION_TIME_UNIT = TimeUnit.NANOSECONDS;

  private int eventLoopPoolSize = DEFAULT_EVENT_LOOP_POOL_SIZE;
  private int workerPoolSize = DEFAULT_WORKER_POOL_SIZE;
  private int internalBlockingPoolSize = DEFAULT_INTERNAL_BLOCKING_POOL_SIZE;
  private long blockedThreadCheckInterval = DEFAULT_BLOCKED_THREAD_CHECK_INTERVAL;
  private long maxEventLoopExecuteTime = DEFAULT_MAX_EVENT_LOOP_EXECUTE_TIME;
  private long maxWorkerExecuteTime = DEFAULT_MAX_WORKER_EXECUTE_TIME;
  private ClusterManager clusterManager;
  private boolean haEnabled = DEFAULT_HA_ENABLED;
  private int quorumSize = DEFAULT_QUORUM_SIZE;
  private String haGroup = DEFAULT_HA_GROUP;
  private MetricsOptions metricsOptions = new MetricsOptions();
  private TracingOptions tracingOptions = new TracingOptions();
  private FileSystemOptions fileSystemOptions = new FileSystemOptions();
  private long warningExceptionTime = DEFAULT_WARNING_EXCEPTION_TIME;
  private EventBusOptions eventBusOptions = new EventBusOptions();
  private AddressResolverOptions addressResolverOptions = new AddressResolverOptions();
  private boolean preferNativeTransport = DEFAULT_PREFER_NATIVE_TRANSPORT;
  private TimeUnit maxEventLoopExecuteTimeUnit = DEFAULT_MAX_EVENT_LOOP_EXECUTE_TIME_UNIT;
  private TimeUnit maxWorkerExecuteTimeUnit = DEFAULT_MAX_WORKER_EXECUTE_TIME_UNIT;
  private TimeUnit warningExceptionTimeUnit = DEFAULT_WARNING_EXCEPTION_TIME_UNIT;
  private TimeUnit blockedThreadCheckIntervalUnit = DEFAULT_BLOCKED_THREAD_CHECK_INTERVAL_UNIT;

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
    this.clusterManager = other.getClusterManager();
    this.haEnabled = other.isHAEnabled();
    this.quorumSize = other.getQuorumSize();
    this.haGroup = other.getHAGroup();
    this.metricsOptions = other.getMetricsOptions() != null ? new MetricsOptions(other.getMetricsOptions()) : null;
    this.fileSystemOptions = other.getFileSystemOptions() != null ? new FileSystemOptions(other.getFileSystemOptions()) : null;
    this.warningExceptionTime = other.warningExceptionTime;
    this.eventBusOptions = new EventBusOptions(other.eventBusOptions);
    this.addressResolverOptions = other.addressResolverOptions != null ? new AddressResolverOptions() : null;
    this.maxEventLoopExecuteTimeUnit = other.maxEventLoopExecuteTimeUnit;
    this.maxWorkerExecuteTimeUnit = other.maxWorkerExecuteTimeUnit;
    this.warningExceptionTimeUnit = other.warningExceptionTimeUnit;
    this.blockedThreadCheckIntervalUnit = other.blockedThreadCheckIntervalUnit;
    this.tracingOptions = other.tracingOptions != null ? other.tracingOptions.copy() : null;
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
   * Is the Vert.x instance clustered?
   *
   * @return true if clustered, false if not
   */
  public boolean isClustered() {
    return eventBusOptions.isClustered();
  }

  /**
   * Set whether or not the Vert.x instance will be clustered.
   *
   * @param clustered if true, the Vert.x instance will be clustered, otherwise not
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setClustered(boolean clustered) {
    eventBusOptions.setClustered(clustered);
    return this;
  }

  /**
   * Get the host name to be used for clustering.
   *
   * @return The host name
   */
  public String getClusterHost() {
    return eventBusOptions.getHost();
  }

  /**
   * Set the hostname to be used for clustering.
   *
   * @param clusterHost the host name to use
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setClusterHost(String clusterHost) {
    this.eventBusOptions.setHost(clusterHost);
    return this;
  }

  /**
   * Get the public facing hostname to be used when clustering.
   *
   * @return the public facing hostname
   */
  public String getClusterPublicHost() {
    return getEventBusOptions().getClusterPublicHost();
  }

  /**
   * Set the public facing hostname to be used for clustering.
   * Sometimes, e.g. when running on certain clouds, the local address the server listens on for clustering is not the same
   * address that other nodes connect to it at, as the OS / cloud infrastructure does some kind of proxying.
   * If this is the case you can specify a public hostname which is different from the hostname the server listens at.
   * The default value is null which means use the same as the cluster hostname.
   *
   * @param clusterPublicHost the public host name to use
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setClusterPublicHost(String clusterPublicHost) {
    getEventBusOptions().setClusterPublicHost(clusterPublicHost);
    return this;
  }

  /**
   * Get the port to be used for clustering
   *
   * @return the port
   */
  public int getClusterPort() {
    return eventBusOptions.getPort();
  }

  /**
   * Set the port to be used for clustering.
   *
   * @param clusterPort the port
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setClusterPort(int clusterPort) {
    eventBusOptions.setPort(clusterPort);
    return this;
  }

  /**
   * Get the public facing port to be used when clustering.
   *
   * @return the public facing port
   */
  public int getClusterPublicPort() {
    return eventBusOptions.getClusterPublicPort();
  }

  /**
   * See {@link #setClusterPublicHost(String)} for an explanation.
   *
   * @param clusterPublicPort the public port to use
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setClusterPublicPort(int clusterPublicPort) {
    getEventBusOptions().setClusterPublicPort(clusterPublicPort);
    return this;
  }

  /**
   * Get the value of cluster ping interval, in ms.
   * <p>
   * Nodes in the cluster ping each other at this interval to determine whether they are still running.
   *
   * @return The value of cluster ping interval
   */
  public long getClusterPingInterval() {
    return getEventBusOptions().getClusterPingInterval();
  }

  /**
   * Set the value of cluster ping interval, in ms.
   *
   * @param clusterPingInterval The value of cluster ping interval, in ms.
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setClusterPingInterval(long clusterPingInterval) {
    eventBusOptions.setClusterPingInterval(clusterPingInterval);
    return this;
  }

  /**
   * Get the value of cluster ping reply interval, in ms.
   * <p>
   * After sending a ping, if a pong is not received in this time, the node will be considered dead.
   *
   * @return the value of cluster ping reply interval
   */
  public long getClusterPingReplyInterval() {
    return eventBusOptions.getClusterPingReplyInterval();
  }

  /**
   * Set the value of cluster ping reply interval, in ms.
   *
   * @param clusterPingReplyInterval The value of cluster ping reply interval, in ms.
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setClusterPingReplyInterval(long clusterPingReplyInterval) {
    eventBusOptions.setClusterPingReplyInterval(clusterPingReplyInterval);
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
   * Get the cluster manager to be used when clustering.
   * <p>
   * If the cluster manager has been programmatically set here, then that will be used when clustering.
   * <p>
   * Otherwise Vert.x attempts to locate a cluster manager on the classpath.
   *
   * @return the cluster manager.
   */
  public ClusterManager getClusterManager() {
    return clusterManager;
  }

  /**
   * Programmatically set the cluster manager to be used when clustering.
   * <p>
   * Only valid if clustered = true.
   * <p>
   * Normally Vert.x will look on the classpath for a cluster manager, but if you want to set one
   * programmatically you can use this method.
   *
   * @param clusterManager the cluster manager
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setClusterManager(ClusterManager clusterManager) {
    this.clusterManager = clusterManager;
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
   * @return wether to prefer the native transport to the JDK transport
   */
  public boolean getPreferNativeTransport() {
    return preferNativeTransport;
  }

  /**
   * Set wether to prefer the native transport to the JDK transport.
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

  public TracingOptions getTracingOptions() {
    return tracingOptions;
  }

  public VertxOptions setTracingOptions(TracingOptions tracingOptions) {
    this.tracingOptions = tracingOptions;
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
        ", clusterManager=" + clusterManager +
        ", haEnabled=" + haEnabled +
        ", preferNativeTransport=" + preferNativeTransport +
        ", quorumSize=" + quorumSize +
        ", haGroup='" + haGroup + '\'' +
        ", metrics=" + metricsOptions +
        ", fileSystemOptions=" + fileSystemOptions +
        ", addressResolver=" + addressResolverOptions.toJson() +
        ", addressResolver=" + addressResolverOptions.toJson() +
        ", eventbus=" + eventBusOptions.toJson() +
        ", warningExceptionTimeUnit=" + warningExceptionTimeUnit +
        ", warningExceptionTime=" + warningExceptionTime +
        '}';
  }
}
