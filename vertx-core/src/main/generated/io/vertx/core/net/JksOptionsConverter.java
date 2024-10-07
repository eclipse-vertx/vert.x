package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.core.net.JksOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.JksOptions} original class using Vert.x codegen.
 */
public class JksOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, JksOptions obj) {
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
            obj.setValue(io.vertx.core.buffer.Buffer.fromJson((String)member.getValue()));
          }
          break;
        case "alias":
          if (member.getValue() instanceof String) {
            obj.setAlias((String)member.getValue());
          }
          break;
        case "aliasPassword":
          if (member.getValue() instanceof String) {
            obj.setAliasPassword((String)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(JksOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(JksOptions obj, java.util.Map<String, Object> json) {
    if (obj.getPassword() != null) {
      json.put("password", obj.getPassword());
    }
    if (obj.getPath() != null) {
      json.put("path", obj.getPath());
    }
    if (obj.getValue() != null) {
      json.put("value", obj.getValue().toJson());
    }
    if (obj.getAlias() != null) {
      json.put("alias", obj.getAlias());
    }
    if (obj.getAliasPassword() != null) {
      json.put("aliasPassword", obj.getAliasPassword());
    }
  }
}
