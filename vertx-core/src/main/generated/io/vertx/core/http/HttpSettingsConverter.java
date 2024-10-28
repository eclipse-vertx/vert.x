package io.vertx.core.http;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.core.http.HttpSettings}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.http.HttpSettings} original class using Vert.x codegen.
 */
public class HttpSettingsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, HttpSettings obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "http2Settings":
          break;
        case "http3Settings":
          break;
      }
    }
  }

   static void toJson(HttpSettings obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(HttpSettings obj, java.util.Map<String, Object> json) {
    if (obj.getHttp2Settings() != null) {
      json.put("http2Settings", obj.getHttp2Settings().toJson());
    }
    if (obj.getHttp3Settings() != null) {
      json.put("http3Settings", obj.getHttp3Settings().toJson());
    }
  }
}
