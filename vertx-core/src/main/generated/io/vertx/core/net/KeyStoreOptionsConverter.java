package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.core.net.KeyStoreOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.KeyStoreOptions} original class using Vert.x codegen.
 */
public class KeyStoreOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, KeyStoreOptions obj) {
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
        case "provider":
          if (member.getValue() instanceof String) {
            obj.setProvider((String)member.getValue());
          }
          break;
        case "type":
          if (member.getValue() instanceof String) {
            obj.setType((String)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(KeyStoreOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(KeyStoreOptions obj, java.util.Map<String, Object> json) {
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
    if (obj.getProvider() != null) {
      json.put("provider", obj.getProvider());
    }
    if (obj.getType() != null) {
      json.put("type", obj.getType());
    }
  }
}
