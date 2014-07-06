/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core;

import io.vertx.core.spi.cluster.ClusterManager;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VertxOptions {

  private int eventLoopPoolSize = 2 * Runtime.getRuntime().availableProcessors();
  private int workerPoolSize = 20;
  private int internalBlockingPoolSize = 20;
  private boolean clustered;
  private String clusterHost = "localhost";
  private int clusterPort = 0;
  private long blockedThreadCheckPeriod = 1000;
  private long maxEventLoopExecuteTime = 2000l * 1000000;
  private long maxWorkerExecuteTime = 1l * 60 * 1000 * 1000000;
  private ClusterManager clusterManager;

  public VertxOptions() {
  }

  public VertxOptions(VertxOptions other) {
    this.eventLoopPoolSize = other.eventLoopPoolSize;
    this.workerPoolSize = other.workerPoolSize;
    this.clustered = other.clustered;
    this.clusterHost = other.clusterHost;
    this.clusterPort = other.clusterPort;
    this.blockedThreadCheckPeriod = other.blockedThreadCheckPeriod;
    this.maxEventLoopExecuteTime = other.maxEventLoopExecuteTime;
    this.maxWorkerExecuteTime = other.maxWorkerExecuteTime;
    this.clusterManager = other.clusterManager;
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
}
