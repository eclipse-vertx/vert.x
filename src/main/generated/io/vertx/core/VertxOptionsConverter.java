package io.vertx.core;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.core.VertxOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.VertxOptions} original class using Vert.x codegen.
 */
public class VertxOptionsConverter {


   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, VertxOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "addressResolverOptions":
          if (member.getValue() instanceof JsonObject) {
            obj.setAddressResolverOptions(new io.vertx.core.dns.AddressResolverOptions((io.vertx.core.json.JsonObject)member.getValue()));
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
        case "eventBusOptions":
          if (member.getValue() instanceof JsonObject) {
            obj.setEventBusOptions(new io.vertx.core.eventbus.EventBusOptions((io.vertx.core.json.JsonObject)member.getValue()));
          }
          break;
        case "eventLoopPoolSize":
          if (member.getValue() instanceof Number) {
            obj.setEventLoopPoolSize(((Number)member.getValue()).intValue());
          }
          break;
        case "fileSystemOptions":
          if (member.getValue() instanceof JsonObject) {
            obj.setFileSystemOptions(new io.vertx.core.file.FileSystemOptions((io.vertx.core.json.JsonObject)member.getValue()));
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
            obj.setMetricsOptions(new io.vertx.core.metrics.MetricsOptions((io.vertx.core.json.JsonObject)member.getValue()));
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
        case "tracingOptions":
          if (member.getValue() instanceof JsonObject) {
            obj.setTracingOptions(new io.vertx.core.tracing.TracingOptions((io.vertx.core.json.JsonObject)member.getValue()));
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
    if (obj.getEventBusOptions() != null) {
      json.put("eventBusOptions", obj.getEventBusOptions().toJson());
    }
    json.put("eventLoopPoolSize", obj.getEventLoopPoolSize());
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
    if (obj.getTracingOptions() != null) {
      json.put("tracingOptions", obj.getTracingOptions().toJson());
    }
    json.put("warningExceptionTime", obj.getWarningExceptionTime());
    if (obj.getWarningExceptionTimeUnit() != null) {
      json.put("warningExceptionTimeUnit", obj.getWarningExceptionTimeUnit().name());
    }
    json.put("workerPoolSize", obj.getWorkerPoolSize());
  }
}
