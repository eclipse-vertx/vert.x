package io.vertx.core;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter for {@link io.vertx.core.VertxOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.VertxOptions} original class using Vert.x codegen.
 */
 class VertxOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, VertxOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "addressResolverOptions":
          if (member.getValue() instanceof JsonObject) {
            obj.setAddressResolverOptions(new io.vertx.core.dns.AddressResolverOptions((JsonObject)member.getValue()));
          }
          break;
        case "blockedThreadCheckInterval":
          if (member.getValue() instanceof Number) {
            obj.setBlockedThreadCheckInterval(((Number)member.getValue()).longValue());
          }
          break;
        case "blockedThreadCheckIntervalUnit":
          if (member.getValue() instanceof String) {
            obj.setBlockedThreadCheckIntervalUnit(java.util.concurrent.TimeUnit.valueOf((String)member.getValue()));
          }
          break;
        case "clusterHost":
          if (member.getValue() instanceof String) {
            obj.setClusterHost((String)member.getValue());
          }
          break;
        case "clusterPingInterval":
          if (member.getValue() instanceof Number) {
            obj.setClusterPingInterval(((Number)member.getValue()).longValue());
          }
          break;
        case "clusterPingReplyInterval":
          if (member.getValue() instanceof Number) {
            obj.setClusterPingReplyInterval(((Number)member.getValue()).longValue());
          }
          break;
        case "clusterPort":
          if (member.getValue() instanceof Number) {
            obj.setClusterPort(((Number)member.getValue()).intValue());
          }
          break;
        case "clusterPublicHost":
          if (member.getValue() instanceof String) {
            obj.setClusterPublicHost((String)member.getValue());
          }
          break;
        case "clusterPublicPort":
          if (member.getValue() instanceof Number) {
            obj.setClusterPublicPort(((Number)member.getValue()).intValue());
          }
          break;
        case "clustered":
          if (member.getValue() instanceof Boolean) {
            obj.setClustered((Boolean)member.getValue());
          }
          break;
        case "eventBusOptions":
          if (member.getValue() instanceof JsonObject) {
            obj.setEventBusOptions(new io.vertx.core.eventbus.EventBusOptions((JsonObject)member.getValue()));
          }
          break;
        case "eventLoopPoolSize":
          if (member.getValue() instanceof Number) {
            obj.setEventLoopPoolSize(((Number)member.getValue()).intValue());
          }
          break;
        case "fileResolverCachingEnabled":
          if (member.getValue() instanceof Boolean) {
            obj.setFileResolverCachingEnabled((Boolean)member.getValue());
          }
          break;
        case "fileSystemOptions":
          if (member.getValue() instanceof JsonObject) {
            obj.setFileSystemOptions(new io.vertx.core.file.FileSystemOptions((JsonObject)member.getValue()));
          }
          break;
        case "haEnabled":
          if (member.getValue() instanceof Boolean) {
            obj.setHAEnabled((Boolean)member.getValue());
          }
          break;
        case "haGroup":
          if (member.getValue() instanceof String) {
            obj.setHAGroup((String)member.getValue());
          }
          break;
        case "internalBlockingPoolSize":
          if (member.getValue() instanceof Number) {
            obj.setInternalBlockingPoolSize(((Number)member.getValue()).intValue());
          }
          break;
        case "maxEventLoopExecuteTime":
          if (member.getValue() instanceof Number) {
            obj.setMaxEventLoopExecuteTime(((Number)member.getValue()).longValue());
          }
          break;
        case "maxEventLoopExecuteTimeUnit":
          if (member.getValue() instanceof String) {
            obj.setMaxEventLoopExecuteTimeUnit(java.util.concurrent.TimeUnit.valueOf((String)member.getValue()));
          }
          break;
        case "maxWorkerExecuteTime":
          if (member.getValue() instanceof Number) {
            obj.setMaxWorkerExecuteTime(((Number)member.getValue()).longValue());
          }
          break;
        case "maxWorkerExecuteTimeUnit":
          if (member.getValue() instanceof String) {
            obj.setMaxWorkerExecuteTimeUnit(java.util.concurrent.TimeUnit.valueOf((String)member.getValue()));
          }
          break;
        case "metricsOptions":
          if (member.getValue() instanceof JsonObject) {
            obj.setMetricsOptions(new io.vertx.core.metrics.MetricsOptions((JsonObject)member.getValue()));
          }
          break;
        case "preferNativeTransport":
          if (member.getValue() instanceof Boolean) {
            obj.setPreferNativeTransport((Boolean)member.getValue());
          }
          break;
        case "quorumSize":
          if (member.getValue() instanceof Number) {
            obj.setQuorumSize(((Number)member.getValue()).intValue());
          }
          break;
        case "warningExceptionTime":
          if (member.getValue() instanceof Number) {
            obj.setWarningExceptionTime(((Number)member.getValue()).longValue());
          }
          break;
        case "warningExceptionTimeUnit":
          if (member.getValue() instanceof String) {
            obj.setWarningExceptionTimeUnit(java.util.concurrent.TimeUnit.valueOf((String)member.getValue()));
          }
          break;
        case "workerPoolSize":
          if (member.getValue() instanceof Number) {
            obj.setWorkerPoolSize(((Number)member.getValue()).intValue());
          }
          break;
      }
    }
  }

   static void toJson(VertxOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(VertxOptions obj, java.util.Map<String, Object> json) {
    if (obj.getAddressResolverOptions() != null) {
      json.put("addressResolverOptions", obj.getAddressResolverOptions().toJson());
    }
    json.put("blockedThreadCheckInterval", obj.getBlockedThreadCheckInterval());
    if (obj.getBlockedThreadCheckIntervalUnit() != null) {
      json.put("blockedThreadCheckIntervalUnit", obj.getBlockedThreadCheckIntervalUnit().name());
    }
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
    json.put("fileResolverCachingEnabled", obj.isFileResolverCachingEnabled());
    if (obj.getFileSystemOptions() != null) {
      json.put("fileSystemOptions", obj.getFileSystemOptions().toJson());
    }
    json.put("haEnabled", obj.isHAEnabled());
    if (obj.getHAGroup() != null) {
      json.put("haGroup", obj.getHAGroup());
    }
    json.put("internalBlockingPoolSize", obj.getInternalBlockingPoolSize());
    json.put("maxEventLoopExecuteTime", obj.getMaxEventLoopExecuteTime());
    if (obj.getMaxEventLoopExecuteTimeUnit() != null) {
      json.put("maxEventLoopExecuteTimeUnit", obj.getMaxEventLoopExecuteTimeUnit().name());
    }
    json.put("maxWorkerExecuteTime", obj.getMaxWorkerExecuteTime());
    if (obj.getMaxWorkerExecuteTimeUnit() != null) {
      json.put("maxWorkerExecuteTimeUnit", obj.getMaxWorkerExecuteTimeUnit().name());
    }
    if (obj.getMetricsOptions() != null) {
      json.put("metricsOptions", obj.getMetricsOptions().toJson());
    }
    json.put("preferNativeTransport", obj.getPreferNativeTransport());
    json.put("quorumSize", obj.getQuorumSize());
    json.put("warningExceptionTime", obj.getWarningExceptionTime());
    if (obj.getWarningExceptionTimeUnit() != null) {
      json.put("warningExceptionTimeUnit", obj.getWarningExceptionTimeUnit().name());
    }
    json.put("workerPoolSize", obj.getWorkerPoolSize());
  }
}
