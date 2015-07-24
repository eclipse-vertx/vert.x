package io.vertx.core;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

public class VertxOptionsHelper {

  public static void fromJson(JsonObject json, VertxOptions obj) {
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
    if (json.getValue("clustered") instanceof Boolean) {
      obj.setClustered((Boolean)json.getValue("clustered"));
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
    json.put("blockedThreadCheckInterval", obj.getBlockedThreadCheckInterval());
    if (obj.getClusterHost() != null) {
      json.put("clusterHost", obj.getClusterHost());
    }
    json.put("clusterPingInterval", obj.getClusterPingInterval());
    json.put("clusterPingReplyInterval", obj.getClusterPingReplyInterval());
    json.put("clusterPort", obj.getClusterPort());
    json.put("clustered", obj.isClustered());
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