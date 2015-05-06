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
import io.vertx.core.json.JsonObject;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.spi.cluster.ClusterManager;

import java.util.Objects;

/**
 * Instances of this class are used to configure {@link io.vertx.core.Vertx} instances.
 * 
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@DataObject
public class VertxOptions {

  /**
   * The default number of event loop threads to be used  = 2 * number of cores on the machine
   */
  public static final int DEFAULT_EVENT_LOOP_POOL_SIZE = 2 * Runtime.getRuntime().availableProcessors();

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
   * The default value of cluster ping interval = 20000 ms.
   */
  public static final long DEFAULT_CLUSTER_PING_INTERVAL = 20000;

  /**
   * The default value of cluster ping reply interval = 20000 ms.
   */
  public static final long DEFAULT_CLUSTER_PING_REPLY_INTERVAL = 20000;

  /**
   * The default value of blocked thread check period = 1000 ms.
   */
  public static final long DEFAULT_BLOCKED_THREAD_CHECK_PERIOD = 1000;

  /**
   * The default value of max event loop execute time = 2000000000 ns (2 seconds)
   */
  public static final long DEFAULT_MAX_EVENT_LOOP_EXECUTE_TIME = 2000l * 1000000;

  /**
   * The default value of max worker execute time = 60000000000 ns (60 seconds)
   */
  public static final long DEFAULT_MAX_WORKER_EXECUTE_TIME = 60l * 1000 * 1000000;

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
   * The default value of metrics enabled false
   */
  public static final boolean DEFAULT_METRICS_ENABLED = false;

  private int eventLoopPoolSize = DEFAULT_EVENT_LOOP_POOL_SIZE;
  private int workerPoolSize = DEFAULT_WORKER_POOL_SIZE;
  private int internalBlockingPoolSize = DEFAULT_INTERNAL_BLOCKING_POOL_SIZE;
  private boolean clustered = DEFAULT_CLUSTERED;
  private String clusterHost = DEFAULT_CLUSTER_HOST;
  private int clusterPort = DEFAULT_CLUSTER_PORT;
  private long clusterPingInterval = DEFAULT_CLUSTER_PING_INTERVAL;
  private long clusterPingReplyInterval = DEFAULT_CLUSTER_PING_REPLY_INTERVAL;
  private long blockedThreadCheckPeriod = DEFAULT_BLOCKED_THREAD_CHECK_PERIOD;
  private long maxEventLoopExecuteTime = DEFAULT_MAX_EVENT_LOOP_EXECUTE_TIME;
  private long maxWorkerExecuteTime = DEFAULT_MAX_WORKER_EXECUTE_TIME;
  private ClusterManager clusterManager;
  private boolean haEnabled = DEFAULT_HA_ENABLED;
  private int quorumSize = DEFAULT_QUORUM_SIZE;
  private String haGroup = DEFAULT_HA_GROUP;
  private MetricsOptions metrics;

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
    this.clustered = other.isClustered();
    this.clusterHost = other.getClusterHost();
    this.clusterPort = other.getClusterPort();
    this.clusterPingInterval = other.getClusterPingInterval();
    this.clusterPingReplyInterval = other.getClusterPingReplyInterval();
    this.blockedThreadCheckPeriod = other.getBlockedThreadCheckPeriod();
    this.maxEventLoopExecuteTime = other.getMaxEventLoopExecuteTime();
    this.maxWorkerExecuteTime = other.getMaxWorkerExecuteTime();
    this.internalBlockingPoolSize = other.getInternalBlockingPoolSize();
    this.clusterManager = other.getClusterManager();
    this.haEnabled = other.isHAEnabled();
    this.quorumSize = other.getQuorumSize();
    this.haGroup = other.getHAGroup();
    this.metrics = other.getMetricsOptions() != null ? new MetricsOptions(other.getMetricsOptions()) : null;
  }

  /**
   * Create an instance from a {@link io.vertx.core.json.JsonObject}
   * 
   * @param json the JsonObject to create it from
   */
  public VertxOptions(JsonObject json) {
    this.eventLoopPoolSize = json.getInteger("eventLoopPoolSize", DEFAULT_EVENT_LOOP_POOL_SIZE);
    this.workerPoolSize = json.getInteger("workerPoolSize", DEFAULT_WORKER_POOL_SIZE);
    this.clustered = json.getBoolean("clustered", DEFAULT_CLUSTERED);
    this.clusterHost = json.getString("clusterHost", DEFAULT_CLUSTER_HOST);
    this.clusterPort = json.getInteger("clusterPort", DEFAULT_CLUSTER_PORT);
    this.clusterPingInterval = json.getLong("clusterPingInterval", DEFAULT_CLUSTER_PING_INTERVAL);
    this.clusterPingReplyInterval = json.getLong("clusterPingReplyInterval", DEFAULT_CLUSTER_PING_REPLY_INTERVAL);
    this.internalBlockingPoolSize = json.getInteger("internalBlockingPoolSize", DEFAULT_INTERNAL_BLOCKING_POOL_SIZE);
    this.blockedThreadCheckPeriod = json.getLong("blockedThreadCheckPeriod", DEFAULT_BLOCKED_THREAD_CHECK_PERIOD);
    this.maxEventLoopExecuteTime = json.getLong("maxEventLoopExecuteTime", DEFAULT_MAX_EVENT_LOOP_EXECUTE_TIME);
    this.maxWorkerExecuteTime = json.getLong("maxWorkerExecuteTime", DEFAULT_MAX_WORKER_EXECUTE_TIME);
    this.haEnabled = json.getBoolean("haEnabled", false);
    this.quorumSize = json.getInteger("quorumSize", DEFAULT_QUORUM_SIZE);
    this.haGroup = json.getString("haGroup", DEFAULT_HA_GROUP);
    JsonObject metricsJson = json.getJsonObject("metricsOptions");
    this.metrics = metricsJson != null ? new MetricsOptions(metricsJson) : null;
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
   * @param eventLoopPoolSize  the number of threads
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
   * @param workerPoolSize  the number of threads
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
   * @return true if clustered, false if not
   */
  public boolean isClustered() {
    return clustered;
  }

  /**
   * Set whether or not the Vert.x instance will be clustered.
   * @param clustered  if true, the Vert.x instance will be clustered, otherwise not
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setClustered(boolean clustered) {
    this.clustered = clustered;
    return this;
  }

  /**
   * Get the host name to be used for clustering.
   *
   * @return The host name
   */
  public String getClusterHost() {
    return clusterHost;
  }

  /**
   * Set the hostname to be used for clustering.
   *
   * @param clusterHost  the host name to use
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setClusterHost(String clusterHost) {
    this.clusterHost = clusterHost;
    return this;
  }

  /**
   * Get the port to be used for clustering
   *
   * @return the port
   */
  public int getClusterPort() {
    return clusterPort;
  }

  /**
   * Set the port to be used for clustering.
   *
   * @param clusterPort  the port
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setClusterPort(int clusterPort) {
    if (clusterPort < 0 || clusterPort > 65535) {
      throw new IllegalArgumentException("clusterPort p must be in range 0 <= p <= 65535");
    }
    this.clusterPort = clusterPort;
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
    return clusterPingInterval;
  }

  /**
   * Set the value of cluster ping interval, in ms.
   *
   * @param clusterPingInterval The value of cluster ping interval, in ms.
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setClusterPingInterval(long clusterPingInterval) {
    if (clusterPingInterval < 1) {
      throw new IllegalArgumentException("clusterPingInterval must be greater than 0");
    }
    this.clusterPingInterval = clusterPingInterval;
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
    return clusterPingReplyInterval;
  }

  /**
   * Set the value of cluster ping reply interval, in ms.
   *
   * @param clusterPingReplyInterval The value of cluster ping reply interval, in ms.
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setClusterPingReplyInterval(long clusterPingReplyInterval) {
    if (clusterPingReplyInterval < 1) {
      throw new IllegalArgumentException("clusterPingReplyInterval must be greater than 0");
    }
    this.clusterPingReplyInterval = clusterPingReplyInterval;
    return this;
  }

  /**
   * Get the value of blocked thread check period, in ms.
   * <p>
   * This setting determines how often Vert.x will check whether event loop threads are executing for too long.
   *
   * @return the value of blocked thread check period, in ms.
   */
  public long getBlockedThreadCheckPeriod() {
    return blockedThreadCheckPeriod;
  }

  /**
   * Sets the value of blocked thread check period, in ms.
   *
   * @param blockedThreadCheckPeriod  the value of blocked thread check period, in ms.
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setBlockedThreadCheckPeriod(long blockedThreadCheckPeriod) {
    if (blockedThreadCheckPeriod < 1) {
      throw new IllegalArgumentException("blockedThreadCheckPeriod must be > 0");
    }
    this.blockedThreadCheckPeriod = blockedThreadCheckPeriod;
    return this;
  }

  /**
   * Get the value of max event loop execute time, in ns.
   * <p>
   * Vert.x will automatically log a warning if it detects that event loop threads haven't returned within this time.
   * <p>
   * This can be used to detect where the user is blocking an event loop thread, contrary to the Golden Rule of the
   * holy Event Loop.
   *
   * @return the value of max event loop execute time, in ms.
   */
  public long getMaxEventLoopExecuteTime() {
    return maxEventLoopExecuteTime;
  }

  /**
   * Sets the value of max event loop execute time, in ns.
   *
   * @param maxEventLoopExecuteTime  the value of max event loop execute time, in ms.
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
   * Get the value of max worker execute time, in ns.
   * <p>
   * Vert.x will automatically log a warning if it detects that worker threads haven't returned within this time.
   * <p>
   * This can be used to detect where the user is blocking a worker thread for too long. Although worker threads
   * can be blocked longer than event loop threads, they shouldn't be blocked for long periods of time.
   *
   * @return The value of max worker execute time, in ms.
   */
  public long getMaxWorkerExecuteTime() {
    return maxWorkerExecuteTime;
  }

  /**
   * Sets the value of max worker execute time, in ns.
   *
   * @param maxWorkerExecuteTime  the value of max worker execute time, in ms.
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
   * @return  the cluster manager.
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
   * @param clusterManager  the cluster manager
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
   * @param haEnabled  true if enabled, false if not.
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setHAEnabled(boolean haEnabled) {
    this.haEnabled = haEnabled;
    return this;
  }

  /**
   * Get the quorum size to be used when HA is enabled.
   *
   * @return  the quorum size
   */
  public int getQuorumSize() {
    return quorumSize;
  }

  /**
   * Set the quorum size to be used when HA is enabled.
   *
   * @param quorumSize  the quorum size
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
    return metrics;
  }

  /**
   * Set the metrics options
   *
   * @param metrics the options
   * @return a reference to this, so the API can be used fluently
   */
  public VertxOptions setMetricsOptions(MetricsOptions metrics) {
    this.metrics = metrics;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    VertxOptions that = (VertxOptions) o;

    if (blockedThreadCheckPeriod != that.blockedThreadCheckPeriod) return false;
    if (clusterPort != that.clusterPort) return false;
    if (clustered != that.clustered) return false;
    if (eventLoopPoolSize != that.eventLoopPoolSize) return false;
    if (haEnabled != that.haEnabled) return false;
    if (internalBlockingPoolSize != that.internalBlockingPoolSize) return false;
    if (maxEventLoopExecuteTime != that.maxEventLoopExecuteTime) return false;
    if (maxWorkerExecuteTime != that.maxWorkerExecuteTime) return false;
    if (quorumSize != that.quorumSize) return false;
    if (workerPoolSize != that.workerPoolSize) return false;
    if (clusterHost != null ? !clusterHost.equals(that.clusterHost) : that.clusterHost != null) return false;
    if (clusterManager != null ? !clusterManager.equals(that.clusterManager) : that.clusterManager != null)
      return false;
    if (haGroup != null ? !haGroup.equals(that.haGroup) : that.haGroup != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = eventLoopPoolSize;
    result = 31 * result + workerPoolSize;
    result = 31 * result + internalBlockingPoolSize;
    result = 31 * result + (clustered ? 1 : 0);
    result = 31 * result + (clusterHost != null ? clusterHost.hashCode() : 0);
    result = 31 * result + clusterPort;
    result = 31 * result + (int) (blockedThreadCheckPeriod ^ (blockedThreadCheckPeriod >>> 32));
    result = 31 * result + (int) (maxEventLoopExecuteTime ^ (maxEventLoopExecuteTime >>> 32));
    result = 31 * result + (int) (maxWorkerExecuteTime ^ (maxWorkerExecuteTime >>> 32));
    result = 31 * result + (clusterManager != null ? clusterManager.hashCode() : 0);
    result = 31 * result + (haEnabled ? 1 : 0);
    result = 31 * result + quorumSize;
    result = 31 * result + (haGroup != null ? haGroup.hashCode() : 0);
    return result;
  }
}
