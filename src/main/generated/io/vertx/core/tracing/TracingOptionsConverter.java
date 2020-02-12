package io.vertx.core.tracing;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.core.tracing.TracingOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.tracing.TracingOptions} original class using Vert.x codegen.
 */
public class TracingOptionsConverter {


   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, TracingOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "enabled":
          if (member.getValue() instanceof Boolean) {
            obj.setEnabled((Boolean)member.getValue());
          }
          break;
      }
    }
  }

   static TracingOptions fromMap(Iterable<java.util.Map.Entry<String, Object>> map) {
    TracingOptions obj = new TracingOptions();
    fromMap(map, obj);
    return obj;
  }

   static void fromMap(Iterable<java.util.Map.Entry<String, Object>> map, TracingOptions obj) {
    for (java.util.Map.Entry<String, Object> member : map) {
      switch (member.getKey()) {
        case "enabled":
          if (member.getValue() instanceof Boolean) {
            obj.setEnabled((Boolean)member.getValue());
          }
          break;
        case "factory":
          if (member.getValue() instanceof io.vertx.core.spi.VertxTracerFactory) {
            obj.setFactory((io.vertx.core.spi.VertxTracerFactory)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(TracingOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(TracingOptions obj, java.util.Map<String, Object> json) {
    json.put("enabled", obj.isEnabled());
  }
}
