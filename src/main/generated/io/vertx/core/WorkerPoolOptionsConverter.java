package io.vertx.core;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx.core.WorkerPoolOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.WorkerPoolOptions} original class using Vert.x codegen.
 */
public class WorkerPoolOptionsConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, WorkerPoolOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "maxExecuteTime":
          if (member.getValue() instanceof Number) {
            obj.setMaxExecuteTime(((Number)member.getValue()).longValue());
          }
          break;
        case "maxExecuteTimeUnit":
          if (member.getValue() instanceof String) {
            obj.setMaxExecuteTimeUnit(java.util.concurrent.TimeUnit.valueOf((String)member.getValue()));
          }
          break;
        case "name":
          if (member.getValue() instanceof String) {
            obj.setName((String)member.getValue());
          }
          break;
        case "size":
          if (member.getValue() instanceof Number) {
            obj.setSize(((Number)member.getValue()).intValue());
          }
          break;
      }
    }
  }

   static void toJson(WorkerPoolOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(WorkerPoolOptions obj, java.util.Map<String, Object> json) {
    json.put("maxExecuteTime", obj.getMaxExecuteTime());
    if (obj.getMaxExecuteTimeUnit() != null) {
      json.put("maxExecuteTimeUnit", obj.getMaxExecuteTimeUnit().name());
    }
    if (obj.getName() != null) {
      json.put("name", obj.getName());
    }
    json.put("size", obj.getSize());
  }
}
