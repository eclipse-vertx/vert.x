/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.vertx.core;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * Converter for {@link io.vertx.core.VertxOptions}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.VertxOptions} original class using Vert.x codegen.
 */
public class VertxOptionsConverter {

  public static void fromJson(JsonObject json, VertxOptions obj) {
    if (json.getValue("addressResolverOptions") instanceof JsonObject) {
      obj.setAddressResolverOptions(new io.vertx.core.dns.AddressResolverOptions((JsonObject)json.getValue("addressResolverOptions")));
    }
    if (json.getValue("blockedThreadCheckInterval") instanceof Number) {
      obj.setBlockedThreadCheckInterval(((Number)json.getValue("blockedThreadCheckInterval")).longValue());
    }
    if (json.getValue("clusterHost") instanceof String) {
      obj.setClusterHost((String)json.getValue("clusterHost"));
    }
    if (json.getValue("clusterPingInterval") instanceof Number) {
      obj.setClusterPingInterval(((Number)json.getValue("clusterPingInterval")).longValue());
    }
    if (json.getValue("clusterPingReplyInterval") instanceof Number) {
      obj.setClusterPingReplyInterval(((Number)json.getValue("clusterPingReplyInterval")).longValue());
    }
    if (json.getValue("clusterPort") instanceof Number) {
      obj.setClusterPort(((Number)json.getValue("clusterPort")).intValue());
    }
    if (json.getValue("clusterPublicHost") instanceof String) {
      obj.setClusterPublicHost((String)json.getValue("clusterPublicHost"));
    }
    if (json.getValue("clusterPublicPort") instanceof Number) {
      obj.setClusterPublicPort(((Number)json.getValue("clusterPublicPort")).intValue());
    }
    if (json.getValue("clustered") instanceof Boolean) {
      obj.setClustered((Boolean)json.getValue("clustered"));
    }
    if (json.getValue("eventBusOptions") instanceof JsonObject) {
      obj.setEventBusOptions(new io.vertx.core.eventbus.EventBusOptions((JsonObject)json.getValue("eventBusOptions")));
    }
    if (json.getValue("eventLoopPoolSize") instanceof Number) {
      obj.setEventLoopPoolSize(((Number)json.getValue("eventLoopPoolSize")).intValue());
    }
    if (json.getValue("haEnabled") instanceof Boolean) {
      obj.setHAEnabled((Boolean)json.getValue("haEnabled"));
    }
    if (json.getValue("haGroup") instanceof String) {
      obj.setHAGroup((String)json.getValue("haGroup"));
    }
    if (json.getValue("internalBlockingPoolSize") instanceof Number) {
      obj.setInternalBlockingPoolSize(((Number)json.getValue("internalBlockingPoolSize")).intValue());
    }
    if (json.getValue("maxEventLoopExecuteTime") instanceof Number) {
      obj.setMaxEventLoopExecuteTime(((Number)json.getValue("maxEventLoopExecuteTime")).longValue());
    }
    if (json.getValue("maxWorkerExecuteTime") instanceof Number) {
      obj.setMaxWorkerExecuteTime(((Number)json.getValue("maxWorkerExecuteTime")).longValue());
    }
    if (json.getValue("metricsOptions") instanceof JsonObject) {
      obj.setMetricsOptions(new io.vertx.core.metrics.MetricsOptions((JsonObject)json.getValue("metricsOptions")));
    }
    if (json.getValue("quorumSize") instanceof Number) {
      obj.setQuorumSize(((Number)json.getValue("quorumSize")).intValue());
    }
    if (json.getValue("warningExceptionTime") instanceof Number) {
      obj.setWarningExceptionTime(((Number)json.getValue("warningExceptionTime")).longValue());
    }
    if (json.getValue("workerPoolSize") instanceof Number) {
      obj.setWorkerPoolSize(((Number)json.getValue("workerPoolSize")).intValue());
    }
  }

  public static void toJson(VertxOptions obj, JsonObject json) {
    if (obj.getAddressResolverOptions() != null) {
      json.put("addressResolverOptions", obj.getAddressResolverOptions().toJson());
    }
    json.put("blockedThreadCheckInterval", obj.getBlockedThreadCheckInterval());
    if (obj.getClusterHost() != null) {
      json.put("clusterHost", obj.getClusterHost());
    }
    json.put("clusterPingInterval", obj.getClusterPingInterval());
    json.put("clusterPingReplyInterval", obj.getClusterPingReplyInterval());
    json.put("clusterPort", obj.getClusterPort());
    if (obj.getClusterPublicHost() != null) {
      json.put("clusterPublicHost", obj.getClusterPublicHost());
    }
    json.put("clusterPublicPort", obj.getClusterPublicPort());
    json.put("clustered", obj.isClustered());
    if (obj.getEventBusOptions() != null) {
      json.put("eventBusOptions", obj.getEventBusOptions().toJson());
    }
    json.put("eventLoopPoolSize", obj.getEventLoopPoolSize());
    json.put("haEnabled", obj.isHAEnabled());
    if (obj.getHAGroup() != null) {
      json.put("haGroup", obj.getHAGroup());
    }
    json.put("internalBlockingPoolSize", obj.getInternalBlockingPoolSize());
    json.put("maxEventLoopExecuteTime", obj.getMaxEventLoopExecuteTime());
    json.put("maxWorkerExecuteTime", obj.getMaxWorkerExecuteTime());
    if (obj.getMetricsOptions() != null) {
      json.put("metricsOptions", obj.getMetricsOptions().toJson());
    }
    json.put("quorumSize", obj.getQuorumSize());
    json.put("warningExceptionTime", obj.getWarningExceptionTime());
    json.put("workerPoolSize", obj.getWorkerPoolSize());
  }
}