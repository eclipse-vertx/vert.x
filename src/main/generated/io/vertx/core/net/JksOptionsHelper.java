package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

public class JksOptionsHelper {

  public static void fromJson(JsonObject json, JksOptions obj) {
    if (json.getValue("password") instanceof String) {
      obj.setPassword((String)json.getValue("password"));
    }
    if (json.getValue("path") instanceof String) {
      obj.setPath((String)json.getValue("path"));
    }
    if (json.getValue("value") instanceof String) {
      obj.setValue(io.vertx.core.buffer.Buffer.buffer(java.util.Base64.getDecoder().decode((String)json.getValue("value"))));
    }
  }

  public static void toJson(JksOptions obj, JsonObject json) {
    if (obj.getPassword() != null) {
      json.put("password", obj.getPassword());
    }
    if (obj.getPath() != null) {
      json.put("path", obj.getPath());
    }
    if (obj.getValue() != null) {
      json.put("value", obj.getValue().getBytes());
    }
  }
}