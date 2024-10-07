package io.vertx.core.http;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.core.http.Http2Settings}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.http.Http2Settings} original class using Vert.x codegen.
 */
public class Http2SettingsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, Http2Settings obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "headerTableSize":
          if (member.getValue() instanceof Number) {
            obj.setHeaderTableSize(((Number)member.getValue()).longValue());
          }
          break;
        case "pushEnabled":
          if (member.getValue() instanceof Boolean) {
            obj.setPushEnabled((Boolean)member.getValue());
          }
          break;
        case "maxConcurrentStreams":
          if (member.getValue() instanceof Number) {
            obj.setMaxConcurrentStreams(((Number)member.getValue()).longValue());
          }
          break;
        case "initialWindowSize":
          if (member.getValue() instanceof Number) {
            obj.setInitialWindowSize(((Number)member.getValue()).intValue());
          }
          break;
        case "maxFrameSize":
          if (member.getValue() instanceof Number) {
            obj.setMaxFrameSize(((Number)member.getValue()).intValue());
          }
          break;
        case "maxHeaderListSize":
          if (member.getValue() instanceof Number) {
            obj.setMaxHeaderListSize(((Number)member.getValue()).longValue());
          }
          break;
      }
    }
  }

   static void toJson(Http2Settings obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(Http2Settings obj, java.util.Map<String, Object> json) {
    json.put("headerTableSize", obj.getHeaderTableSize());
    json.put("pushEnabled", obj.isPushEnabled());
    json.put("maxConcurrentStreams", obj.getMaxConcurrentStreams());
    json.put("initialWindowSize", obj.getInitialWindowSize());
    json.put("maxFrameSize", obj.getMaxFrameSize());
    json.put("maxHeaderListSize", obj.getMaxHeaderListSize());
  }
}
