package io.vertx.core.metrics;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.core.metrics.MetricsOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.metrics.MetricsOptions} original class using Vert.x codegen.
 */
public class MetricsOptionsConverter {


   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, MetricsOptions obj) {
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

   static MetricsOptions fromMap(Iterable<java.util.Map.Entry<String, Object>> map) {
    MetricsOptions obj = new MetricsOptions();
    fromMap(map, obj);
    return obj;
  }

   static void fromMap(Iterable<java.util.Map.Entry<String, Object>> map, MetricsOptions obj) {
    for (java.util.Map.Entry<String, Object> member : map) {
      switch (member.getKey()) {
        case "enabled":
          if (member.getValue() instanceof Boolean) {
            obj.setEnabled((Boolean)member.getValue());
          }
          break;
        case "factory":
          if (member.getValue() instanceof io.vertx.core.spi.VertxMetricsFactory) {
            obj.setFactory((io.vertx.core.spi.VertxMetricsFactory)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(MetricsOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(MetricsOptions obj, java.util.Map<String, Object> json) {
    json.put("enabled", obj.isEnabled());
  }
}
