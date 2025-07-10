package io.vertx.core.http;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.core.http.StreamPriority}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.http.StreamPriority} original class using Vert.x codegen.
 */
public class StreamPriorityConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, StreamPriority obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "weight":
          if (member.getValue() instanceof Number) {
            obj.setWeight(((Number)member.getValue()).shortValue());
          }
          break;
        case "dependency":
          if (member.getValue() instanceof Number) {
            obj.setDependency(((Number)member.getValue()).intValue());
          }
          break;
        case "exclusive":
          if (member.getValue() instanceof Boolean) {
            obj.setExclusive((Boolean)member.getValue());
          }
          break;
        case "http3Urgency":
          if (member.getValue() instanceof Number) {
            obj.setHttp3Urgency(((Number)member.getValue()).intValue());
          }
          break;
        case "http3Incremental":
          if (member.getValue() instanceof Boolean) {
            obj.setHttp3Incremental((Boolean)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(StreamPriority obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(StreamPriority obj, java.util.Map<String, Object> json) {
    json.put("weight", obj.getWeight());
    json.put("dependency", obj.getDependency());
    json.put("exclusive", obj.isExclusive());
    json.put("http3Urgency", obj.getHttp3Urgency());
    json.put("http3Incremental", obj.isHttp3Incremental());
  }
}
