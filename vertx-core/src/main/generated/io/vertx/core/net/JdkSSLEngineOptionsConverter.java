package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * Converter and mapper for {@link io.vertx.core.net.JdkSSLEngineOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.JdkSSLEngineOptions} original class using Vert.x codegen.
 */
public class JdkSSLEngineOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, JdkSSLEngineOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "useWorkerThread":
          if (member.getValue() instanceof Boolean) {
            obj.setUseWorkerThread((Boolean)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(JdkSSLEngineOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(JdkSSLEngineOptions obj, java.util.Map<String, Object> json) {
    json.put("useWorkerThread", obj.getUseWorkerThread());
  }
}
