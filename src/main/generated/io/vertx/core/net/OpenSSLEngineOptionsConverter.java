package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx.core.net.OpenSSLEngineOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.OpenSSLEngineOptions} original class using Vert.x codegen.
 */
public class OpenSSLEngineOptionsConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, OpenSSLEngineOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "useWorkerThread":
          if (member.getValue() instanceof Boolean) {
            obj.setUseWorkerThread((Boolean)member.getValue());
          }
          break;
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
    json.put("useWorkerThread", obj.getUseWorkerThread());
    json.put("sessionCacheEnabled", obj.isSessionCacheEnabled());
  }
}
