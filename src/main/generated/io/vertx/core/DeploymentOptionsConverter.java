package io.vertx.core;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import io.vertx.core.spi.json.JsonCodec;

/**
 * Converter and Codec for {@link io.vertx.core.DeploymentOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.DeploymentOptions} original class using Vert.x codegen.
 */
public class DeploymentOptionsConverter implements JsonCodec<DeploymentOptions, JsonObject> {

  public static final DeploymentOptionsConverter INSTANCE = new DeploymentOptionsConverter();

  @Override public JsonObject encode(DeploymentOptions value) { return (value != null) ? value.toJson() : null; }

  @Override public DeploymentOptions decode(JsonObject value) { return (value != null) ? new DeploymentOptions(value) : null; }

  @Override public Class<DeploymentOptions> getTargetClass() { return DeploymentOptions.class; }

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, DeploymentOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "config":
          if (member.getValue() instanceof JsonObject) {
            obj.setConfig(((JsonObject)member.getValue()).copy());
          }
          break;
        case "extraClasspath":
          if (member.getValue() instanceof JsonArray) {
            java.util.ArrayList<java.lang.String> list =  new java.util.ArrayList<>();
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof String)
                list.add((String)item);
            });
            obj.setExtraClasspath(list);
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
        case "isolatedClasses":
          if (member.getValue() instanceof JsonArray) {
            java.util.ArrayList<java.lang.String> list =  new java.util.ArrayList<>();
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof String)
                list.add((String)item);
            });
            obj.setIsolatedClasses(list);
          }
          break;
        case "isolationGroup":
          if (member.getValue() instanceof String) {
            obj.setIsolationGroup((String)member.getValue());
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
        case "worker":
          if (member.getValue() instanceof Boolean) {
            obj.setWorker((Boolean)member.getValue());
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
    if (obj.getExtraClasspath() != null) {
      JsonArray array = new JsonArray();
      obj.getExtraClasspath().forEach(item -> array.add(item));
      json.put("extraClasspath", array);
    }
    json.put("ha", obj.isHa());
    json.put("instances", obj.getInstances());
    if (obj.getIsolatedClasses() != null) {
      JsonArray array = new JsonArray();
      obj.getIsolatedClasses().forEach(item -> array.add(item));
      json.put("isolatedClasses", array);
    }
    if (obj.getIsolationGroup() != null) {
      json.put("isolationGroup", obj.getIsolationGroup());
    }
    json.put("maxWorkerExecuteTime", obj.getMaxWorkerExecuteTime());
    if (obj.getMaxWorkerExecuteTimeUnit() != null) {
      json.put("maxWorkerExecuteTimeUnit", obj.getMaxWorkerExecuteTimeUnit().name());
    }
    json.put("worker", obj.isWorker());
    if (obj.getWorkerPoolName() != null) {
      json.put("workerPoolName", obj.getWorkerPoolName());
    }
    json.put("workerPoolSize", obj.getWorkerPoolSize());
  }
}
