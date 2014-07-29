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

import io.vertx.codegen.annotations.Options;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.VertxOptionsFactory;
import io.vertx.core.spi.cluster.ClusterManager;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@Options
public interface VertxOptions {

  static VertxOptions options() {
    return factory.newOptions();
  }

  static VertxOptions copiedOptions(VertxOptions other) {
    return factory.copiedOptions(other);
  }

  static VertxOptions optionsFromJson(JsonObject json) {
    return factory.optionsFromJson(json);
  }

  int getEventLoopPoolSize();

  VertxOptions setEventLoopPoolSize(int eventLoopPoolSize);

  int getWorkerPoolSize();

  VertxOptions setWorkerPoolSize(int workerPoolSize);

  boolean isClustered();

  VertxOptions setClustered(boolean clustered);

  String getClusterHost();

  VertxOptions setClusterHost(String clusterHost);

  int getClusterPort();

  VertxOptions setClusterPort(int clusterPort);

  long getBlockedThreadCheckPeriod();

  VertxOptions setBlockedThreadCheckPeriod(long blockedThreadCheckPeriod);

  long getMaxEventLoopExecuteTime();

  VertxOptions setMaxEventLoopExecuteTime(long maxEventLoopExecuteTime);

  long getMaxWorkerExecuteTime();

  VertxOptions setMaxWorkerExecuteTime(long maxWorkerExecuteTime);

  ClusterManager getClusterManager();

  VertxOptions setClusterManager(ClusterManager clusterManager);

  int getInternalBlockingPoolSize();

  VertxOptions setInternalBlockingPoolSize(int internalBlockingPoolSize);

  long getProxyOperationTimeout();

  VertxOptions setProxyOperationTimeout(long proxyOperationTimeout);

  static final VertxOptionsFactory factory = ServiceHelper.loadFactory(VertxOptionsFactory.class);

}
