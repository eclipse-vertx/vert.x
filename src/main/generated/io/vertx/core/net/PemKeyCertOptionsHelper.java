package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

public class PemKeyCertOptionsHelper {

  public static void fromJson(JsonObject json, PemKeyCertOptions obj) {
    if (json.getValue("certPath") instanceof String) {
      obj.setCertPath((String)json.getValue("certPath"));
    }
    if (json.getValue("certValue") instanceof String) {
      obj.setCertValue(io.vertx.core.buffer.Buffer.buffer(java.util.Base64.getDecoder().decode((String)json.getValue("certValue"))));
    }
    if (json.getValue("keyPath") instanceof String) {
      obj.setKeyPath((String)json.getValue("keyPath"));
    }
    if (json.getValue("keyValue") instanceof String) {
      obj.setKeyValue(io.vertx.core.buffer.Buffer.buffer(java.util.Base64.getDecoder().decode((String)json.getValue("keyValue"))));
    }
  }

  public static void toJson(PemKeyCertOptions obj, JsonObject json) {
    if (obj.getCertPath() != null) {
      json.put("certPath", obj.getCertPath());
    }
    if (obj.getCertValue() != null) {
      json.put("certValue", obj.getCertValue().getBytes());
    }
    if (obj.getKeyPath() != null) {
      json.put("keyPath", obj.getKeyPath());
    }
    if (obj.getKeyValue() != null) {
      json.put("keyValue", obj.getKeyValue().getBytes());
    }
  }
}