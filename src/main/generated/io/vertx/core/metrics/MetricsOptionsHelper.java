package io.vertx.core.metrics;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

public class MetricsOptionsHelper {

  public static void fromJson(JsonObject json, MetricsOptions obj) {
    if (json.getValue("enabled") instanceof Boolean) {
      obj.setEnabled((Boolean)json.getValue("enabled"));
    }
  }

  public static void toJson(MetricsOptions obj, JsonObject json) {
    json.put("enabled", obj.isEnabled());
  }
}