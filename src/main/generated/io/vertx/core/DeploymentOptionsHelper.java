package io.vertx.core;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

public class DeploymentOptionsHelper {

  public static void fromJson(JsonObject json, DeploymentOptions obj) {
    if (json.getValue("config") instanceof JsonObject) {
      obj.setConfig(((JsonObject)json.getValue("config")).copy());
    }
    if (json.getValue("extraClasspath") instanceof JsonArray) {
      obj.setExtraClasspath(
          ((java.util.List<?>)json.getJsonArray("extraClasspath").getList()).
              stream().
              map(item -> (String)item).
              collect(java.util.stream.Collectors.toList()));
    }
    if (json.getValue("ha") instanceof Boolean) {
      obj.setHa((Boolean)json.getValue("ha"));
    }
    if (json.getValue("instances") instanceof Number) {
      obj.setInstances(((Number)json.getValue("instances")).intValue());
    }
    if (json.getValue("isolatedClasses") instanceof JsonArray) {
      obj.setIsolatedClasses(
          ((java.util.List<?>)json.getJsonArray("isolatedClasses").getList()).
              stream().
              map(item -> (String)item).
              collect(java.util.stream.Collectors.toList()));
    }
    if (json.getValue("isolationGroup") instanceof String) {
      obj.setIsolationGroup((String)json.getValue("isolationGroup"));
    }
    if (json.getValue("multiThreaded") instanceof Boolean) {
      obj.setMultiThreaded((Boolean)json.getValue("multiThreaded"));
    }
    if (json.getValue("worker") instanceof Boolean) {
      obj.setWorker((Boolean)json.getValue("worker"));
    }
  }

  public static void toJson(DeploymentOptions obj, JsonObject json) {
    if (obj.getConfig() != null) {
      json.put("config", obj.getConfig());
    }
    if (obj.getExtraClasspath() != null) {
      json.put("extraClasspath", new JsonArray(
          obj.getExtraClasspath().
              stream().
              map(item -> item).
              collect(java.util.stream.Collectors.toList())));
    }
    json.put("ha", obj.isHa());
    json.put("instances", obj.getInstances());
    if (obj.getIsolatedClasses() != null) {
      json.put("isolatedClasses", new JsonArray(
          obj.getIsolatedClasses().
              stream().
              map(item -> item).
              collect(java.util.stream.Collectors.toList())));
    }
    if (obj.getIsolationGroup() != null) {
      json.put("isolationGroup", obj.getIsolationGroup());
    }
    json.put("multiThreaded", obj.isMultiThreaded());
    json.put("worker", obj.isWorker());
  }
}