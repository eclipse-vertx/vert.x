/*
 * Copyright 2014 Red Hat, Inc.
 *
 *   Red Hat licenses this file to you under the Apache License, version 2.0
 *   (the "License"); you may not use this file except in compliance with the
 *   License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *   License for the specific language governing permissions and limitations
 *   under the License.
 */

package io.vertx.core.impl;

import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VertxOptionsImpl implements VertxOptions{

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
  public static final boolean DEFAULT_METRICS_ENABLED = false;
  public static final boolean DEFAULT_JMX_ENABLED = false;

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
  private boolean metricsEnabled = DEFAULT_METRICS_ENABLED;
  private boolean jmxEnabled = DEFAULT_JMX_ENABLED;
  private String jmxDomain;

  VertxOptionsImpl() {
  }

  VertxOptionsImpl(VertxOptions other) {
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
    this.metricsEnabled = other.isMetricsEnabled();
    this.jmxEnabled = other.isJmxEnabled();
    this.jmxDomain = other.getJmxDomain();
  }

  VertxOptionsImpl(JsonObject json) {
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
    this.metricsEnabled = json.getBoolean("metricsEnabled", DEFAULT_METRICS_ENABLED);
    this.jmxEnabled = json.getBoolean("jmxEnabled", DEFAULT_METRICS_ENABLED);
    this.jmxDomain = json.getString("jmxDomain");
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

  @Override
  public boolean isHAEnabled() {
    return haEnabled;
  }

  @Override
  public VertxOptions setHAEnabled(boolean haEnabled) {
    this.haEnabled = haEnabled;
    return this;
  }

  @Override
  public int getQuorumSize() {
    return quorumSize;
  }

  @Override
  public VertxOptions setQuorumSize(int quorumSize) {
    if (quorumSize < 1) {
      throw new IllegalArgumentException("quorumSize should be >= 1");
    }
    this.quorumSize = quorumSize;
    return this;
  }

  @Override
  public String getHAGroup() {
    return haGroup;
  }

  @Override
  public VertxOptions setHAGroup(String haGroup) {
    this.haGroup = haGroup;
    return this;
  }

  public VertxOptions setMetricsEnabled(boolean enable) {
    this.metricsEnabled = enable;
    return this;
  }

  @Override
  public boolean isMetricsEnabled() {
    return metricsEnabled;
  }

  public boolean isJmxEnabled() {
    return jmxEnabled;
  }

  public VertxOptions setJmxEnabled(boolean jmxEnabled) {
    this.jmxEnabled = jmxEnabled;
    if (jmxEnabled) metricsEnabled = true;
    return this;
  }

  public String getJmxDomain() {
    return jmxDomain;
  }

  public VertxOptions setJmxDomain(String jmxDomain) {
    this.jmxDomain = jmxDomain;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    VertxOptionsImpl that = (VertxOptionsImpl) o;

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
    if (proxyOperationTimeout != that.getProxyOperationTimeout()) return false;
    if (metricsEnabled != that.isMetricsEnabled()) return false;
    if (jmxEnabled != that.isJmxEnabled()) return false;
    if (jmxDomain != null ? !jmxDomain.equals(that.getJmxDomain()) : that.getJmxDomain() != null) return false;

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
    result = 31 * result + (metricsEnabled ? 1 : 0);
    result = 31 * result + (jmxEnabled ? 1 : 0);
    result = 31 * result + (jmxDomain != null ? jmxDomain.hashCode() : 0);
    return result;
  }
}
