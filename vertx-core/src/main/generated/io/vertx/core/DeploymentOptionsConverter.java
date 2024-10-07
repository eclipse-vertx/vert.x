package io.vertx.core;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.core.DeploymentOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.DeploymentOptions} original class using Vert.x codegen.
 */
public class DeploymentOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, DeploymentOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "config":
          if (member.getValue() instanceof JsonObject) {
            obj.setConfig(((JsonObject)member.getValue()).copy());
          }
          break;
        case "threadingModel":
          if (member.getValue() instanceof String) {
            obj.setThreadingModel(io.vertx.core.ThreadingModel.valueOf((String)member.getValue()));
          }
          break;
        case "ha":
          if (member.getValue() instanceof Boolean) {
            obj.setHa((Boolean)member.getValue());
          }
          break;
        case "instances":
          if (member.getValue() instanceof Number) {
            obj.setInstances(((Number)member.getValue()).intValue());
          }
          break;
        case "workerPoolName":
          if (member.getValue() instanceof String) {
            obj.setWorkerPoolName((String)member.getValue());
          }
          break;
        case "workerPoolSize":
          if (member.getValue() instanceof Number) {
            obj.setWorkerPoolSize(((Number)member.getValue()).intValue());
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
      }
    }
  }

   static void toJson(DeploymentOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(DeploymentOptions obj, java.util.Map<String, Object> json) {
    if (obj.getConfig() != null) {
      json.put("config", obj.getConfig());
    }
    if (obj.getThreadingModel() != null) {
      json.put("threadingModel", obj.getThreadingModel().name());
    }
    json.put("ha", obj.isHa());
    json.put("instances", obj.getInstances());
    if (obj.getWorkerPoolName() != null) {
      json.put("workerPoolName", obj.getWorkerPoolName());
    }
    json.put("workerPoolSize", obj.getWorkerPoolSize());
    json.put("maxWorkerExecuteTime", obj.getMaxWorkerExecuteTime());
    if (obj.getMaxWorkerExecuteTimeUnit() != null) {
      json.put("maxWorkerExecuteTimeUnit", obj.getMaxWorkerExecuteTimeUnit().name());
    }
  }
}
