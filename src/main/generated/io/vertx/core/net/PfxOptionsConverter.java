package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter for {@link io.vertx.core.net.PfxOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.PfxOptions} original class using Vert.x codegen.
 */
 class PfxOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, PfxOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "password":
          if (member.getValue() instanceof String) {
            obj.setPassword((String)member.getValue());
          }
          break;
        case "path":
          if (member.getValue() instanceof String) {
            obj.setPath((String)member.getValue());
          }
          break;
        case "value":
          if (member.getValue() instanceof String) {
            obj.setValue(io.vertx.core.buffer.Buffer.buffer(java.util.Base64.getDecoder().decode((String)member.getValue())));
          }
          break;
      }
    }
  }

   static void toJson(PfxOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(PfxOptions obj, java.util.Map<String, Object> json) {
    if (obj.getPassword() != null) {
      json.put("password", obj.getPassword());
    }
    if (obj.getPath() != null) {
      json.put("path", obj.getPath());
    }
    if (obj.getValue() != null) {
      json.put("value", java.util.Base64.getEncoder().encodeToString(obj.getValue().getBytes()));
    }
  }
}
