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

import io.vertx.codegen.annotations.Options;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@Options
public class VertxOptions {

  public static final int DEFAULT_EVENTLOOPPOOLSIZE = 2 * Runtime.getRuntime().availableProcessors();
  public static final int DEFAULT_WORKERPOOLSIZE = 20;
  public static final int DEFAULT_INTERNALBLOCKINGPOOLSIZE = 20;
  public static final boolean DEFAULT_CLUSTERED = false;
  public static final String DEFAULT_CLUSTERHOST = "localhost";
  public static final int DEFAULT_CLUSTERPORT = 0;
  public static final int DEFAULT_BLOCKEDTHREADCHECKPERIOD = 1000;
  public static final long DEFAULT_MAXEVENTLOOPEXECUTETIME = 2000l * 1000000;
  public static final long DEFAULT_MAXWORKEREXECUTETIME = 1l * 60 * 1000 * 1000000;
  public static final int DEFAULT_PROXYOPERATIONTIMEOUT = 10 * 1000;
  public static final int DEFAULT_QUORUMSIZE = 1;

  private int eventLoopPoolSize = DEFAULT_EVENTLOOPPOOLSIZE;
  private int workerPoolSize = DEFAULT_WORKERPOOLSIZE;
  private int internalBlockingPoolSize = DEFAULT_INTERNALBLOCKINGPOOLSIZE;
  private boolean clustered = DEFAULT_CLUSTERED;
  private String clusterHost = DEFAULT_CLUSTERHOST;
  private int clusterPort = DEFAULT_CLUSTERPORT;
  private long blockedThreadCheckPeriod = DEFAULT_BLOCKEDTHREADCHECKPERIOD;
  private long maxEventLoopExecuteTime = DEFAULT_MAXEVENTLOOPEXECUTETIME;
  private long maxWorkerExecuteTime = DEFAULT_MAXWORKEREXECUTETIME;
  private ClusterManager clusterManager;
  private long proxyOperationTimeout = DEFAULT_PROXYOPERATIONTIMEOUT;
  private boolean haEnabled;
  private int quorumSize = DEFAULT_QUORUMSIZE;
  private String haGroup;

  public VertxOptions() {
  }

  public VertxOptions(VertxOptions other) {
    this.eventLoopPoolSize = other.getEventLoopPoolSize();
    this.workerPoolSize = other.getWorkerPoolSize();
    this.clustered = other.isClustered();
    this.clusterHost = other.getClusterHost();
    this.clusterPort = other.getClusterPort();
    this.blockedThreadCheckPeriod = other.getBlockedThreadCheckPeriod();
    this.maxEventLoopExecuteTime = other.getMaxEventLoopExecuteTime();
    this.maxWorkerExecuteTime = other.getMaxWorkerExecuteTime();
    this.internalBlockingPoolSize = other.getInternalBlockingPoolSize();
    this.proxyOperationTimeout = other.getProxyOperationTimeout();
    this.clusterManager = other.getClusterManager();
    this.haEnabled = other.isHAEnabled();
    this.quorumSize = other.getQuorumSize();
    this.haGroup = other.getHAGroup();
  }

  public VertxOptions(JsonObject json) {
    this.proxyOperationTimeout = json.getInteger("proxyOperationTimeout", DEFAULT_PROXYOPERATIONTIMEOUT);
    this.eventLoopPoolSize = json.getInteger("eventLoopPoolSize", DEFAULT_EVENTLOOPPOOLSIZE);
    this.workerPoolSize = json.getInteger("workerPoolSize", DEFAULT_WORKERPOOLSIZE);
    this.clustered = json.getBoolean("clustered", DEFAULT_CLUSTERED);
    this.clusterHost = json.getString("clusterHost", DEFAULT_CLUSTERHOST);
    this.clusterPort = json.getInteger("clusterPort", DEFAULT_CLUSTERPORT);
    this.internalBlockingPoolSize = json.getInteger("internalBlockingPoolSize", DEFAULT_INTERNALBLOCKINGPOOLSIZE);
    this.blockedThreadCheckPeriod = json.getLong("blockedThreadCheckPeriod", DEFAULT_BLOCKEDTHREADCHECKPERIOD);
    this.maxEventLoopExecuteTime = json.getLong("maxEventLoopExecuteTime", DEFAULT_MAXEVENTLOOPEXECUTETIME);
    this.maxWorkerExecuteTime = json.getLong("maxWorkerExecuteTime", DEFAULT_MAXWORKEREXECUTETIME);
    this.haEnabled = json.getBoolean("haEnabled", false);
    this.quorumSize = json.getInteger("quorumSize", DEFAULT_QUORUMSIZE);
    this.haGroup = json.getString("haGroup", null);
  }

  public int getEventLoopPoolSize() {
    return eventLoopPoolSize;
  }

  public VertxOptions setEventLoopPoolSize(int eventLoopPoolSize) {
    if (eventLoopPoolSize < 1) {
      throw new IllegalArgumentException("eventLoopPoolSize must be > 0");
    }
    this.eventLoopPoolSize = eventLoopPoolSize;
    return this;
  }

  public int getWorkerPoolSize() {
    return workerPoolSize;
  }

  public VertxOptions setWorkerPoolSize(int workerPoolSize) {
    if (workerPoolSize < 1) {
      throw new IllegalArgumentException("workerPoolSize must be > 0");
    }
    this.workerPoolSize = workerPoolSize;
    return this;
  }

  public boolean isClustered() {
    return clustered;
  }

  public VertxOptions setClustered(boolean clustered) {
    this.clustered = clustered;
    return this;
  }

  public String getClusterHost() {
    return clusterHost;
  }

  public VertxOptions setClusterHost(String clusterHost) {
    this.clusterHost = clusterHost;
    return this;
  }

  public int getClusterPort() {
    return clusterPort;
  }

  public VertxOptions setClusterPort(int clusterPort) {
    if (clusterPort < 0 || clusterPort > 65535) {
      throw new IllegalArgumentException("clusterPort p must be in range 0 <= p <= 65535");
    }
    this.clusterPort = clusterPort;
    return this;
  }

  public long getBlockedThreadCheckPeriod() {
    return blockedThreadCheckPeriod;
  }

  public VertxOptions setBlockedThreadCheckPeriod(long blockedThreadCheckPeriod) {
    if (blockedThreadCheckPeriod < 1) {
      throw new IllegalArgumentException("blockedThreadCheckPeriod must be > 0");
    }
    this.blockedThreadCheckPeriod = blockedThreadCheckPeriod;
    return this;
  }

  public long getMaxEventLoopExecuteTime() {
    return maxEventLoopExecuteTime;
  }

  public VertxOptions setMaxEventLoopExecuteTime(long maxEventLoopExecuteTime) {
    if (maxEventLoopExecuteTime < 1) {
      throw new IllegalArgumentException("maxEventLoopExecuteTime must be > 0");
    }
    this.maxEventLoopExecuteTime = maxEventLoopExecuteTime;
    return this;
  }

  public long getMaxWorkerExecuteTime() {
    return maxWorkerExecuteTime;
  }

  public VertxOptions setMaxWorkerExecuteTime(long maxWorkerExecuteTime) {
    if (maxWorkerExecuteTime < 1) {
      throw new IllegalArgumentException("maxWorkerpExecuteTime must be > 0");
    }
    this.maxWorkerExecuteTime = maxWorkerExecuteTime;
    return this;
  }

  public ClusterManager getClusterManager() {
    return clusterManager;
  }

  public VertxOptions setClusterManager(ClusterManager clusterManager) {
    this.clusterManager = clusterManager;
    return this;
  }

  public int getInternalBlockingPoolSize() {
    return internalBlockingPoolSize;
  }

  public VertxOptions setInternalBlockingPoolSize(int internalBlockingPoolSize) {
    if (internalBlockingPoolSize < 1) {
      throw new IllegalArgumentException("internalBlockingPoolSize must be > 0");
    }
    this.internalBlockingPoolSize = internalBlockingPoolSize;
    return this;
  }

  public long getProxyOperationTimeout() {
    return proxyOperationTimeout;
  }

  public VertxOptions setProxyOperationTimeout(long proxyOperationTimeout) {
    if (proxyOperationTimeout < 1) {
      throw new IllegalArgumentException("proxyOperationTimeout must be > 0");
    }
    this.proxyOperationTimeout = proxyOperationTimeout;
    return this;
  }

  public boolean isHAEnabled() {
    return haEnabled;
  }

  public VertxOptions setHAEnabled(boolean haEnabled) {
    this.haEnabled = haEnabled;
    return this;
  }

  public int getQuorumSize() {
    return quorumSize;
  }

  public VertxOptions setQuorumSize(int quorumSize) {
    if (quorumSize < 1) {
      throw new IllegalArgumentException("quorumSize should be >= 1");
    }
    this.quorumSize = quorumSize;
    return this;
  }

  public String getHAGroup() {
    return haGroup;
  }

  public VertxOptions setHAGroup(String haGroup) {
    this.haGroup = haGroup;
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
    if (proxyOperationTimeout != that.proxyOperationTimeout) return false;
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
    result = 31 * result + (int) (proxyOperationTimeout ^ (proxyOperationTimeout >>> 32));
    result = 31 * result + (haEnabled ? 1 : 0);
    result = 31 * result + quorumSize;
    result = 31 * result + (haGroup != null ? haGroup.hashCode() : 0);
    return result;
  }
}
