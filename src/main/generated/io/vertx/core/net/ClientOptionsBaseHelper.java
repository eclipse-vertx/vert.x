package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

public class ClientOptionsBaseHelper {

  public static void fromJson(JsonObject json, ClientOptionsBase obj) {
    if (json.getValue("connectTimeout") instanceof Number) {
      obj.setConnectTimeout(((Number)json.getValue("connectTimeout")).intValue());
    }
    if (json.getValue("trustAll") instanceof Boolean) {
      obj.setTrustAll((Boolean)json.getValue("trustAll"));
    }
  }

  public static void toJson(ClientOptionsBase obj, JsonObject json) {
    json.put("connectTimeout", obj.getConnectTimeout());
    json.put("trustAll", obj.isTrustAll());
  }
}