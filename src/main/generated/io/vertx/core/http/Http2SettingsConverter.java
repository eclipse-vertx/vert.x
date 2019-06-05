package io.vertx.core.http;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import io.vertx.core.spi.json.JsonCodec;

/**
 * Converter and Codec for {@link io.vertx.core.http.Http2Settings}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.http.Http2Settings} original class using Vert.x codegen.
 */
public class Http2SettingsConverter implements JsonCodec<Http2Settings, JsonObject> {

  public static final Http2SettingsConverter INSTANCE = new Http2SettingsConverter();

  @Override public JsonObject encode(Http2Settings value) { return (value != null) ? value.toJson() : null; }

  @Override public Http2Settings decode(JsonObject value) { return (value != null) ? new Http2Settings(value) : null; }

  @Override public Class<Http2Settings> getTargetClass() { return Http2Settings.class; }

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, Http2Settings obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "headerTableSize":
          if (member.getValue() instanceof Number) {
            obj.setHeaderTableSize(((Number)member.getValue()).longValue());
          }
          break;
        case "initialWindowSize":
          if (member.getValue() instanceof Number) {
            obj.setInitialWindowSize(((Number)member.getValue()).intValue());
          }
          break;
        case "maxConcurrentStreams":
          if (member.getValue() instanceof Number) {
            obj.setMaxConcurrentStreams(((Number)member.getValue()).longValue());
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
        case "pushEnabled":
          if (member.getValue() instanceof Boolean) {
            obj.setPushEnabled((Boolean)member.getValue());
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
    json.put("initialWindowSize", obj.getInitialWindowSize());
    json.put("maxConcurrentStreams", obj.getMaxConcurrentStreams());
    json.put("maxFrameSize", obj.getMaxFrameSize());
    json.put("maxHeaderListSize", obj.getMaxHeaderListSize());
    json.put("pushEnabled", obj.isPushEnabled());
  }
}
