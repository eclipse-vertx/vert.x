package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter for {@link io.vertx.core.net.OpenSSLEngineOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.OpenSSLEngineOptions} original class using Vert.x codegen.
 */
 class OpenSSLEngineOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, OpenSSLEngineOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "sessionCacheEnabled":
          if (member.getValue() instanceof Boolean) {
            obj.setSessionCacheEnabled((Boolean)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(OpenSSLEngineOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(OpenSSLEngineOptions obj, java.util.Map<String, Object> json) {
    json.put("sessionCacheEnabled", obj.isSessionCacheEnabled());
  }
}
