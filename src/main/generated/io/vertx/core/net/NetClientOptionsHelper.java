package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

public class NetClientOptionsHelper {

  public static void fromJson(JsonObject json, NetClientOptions obj) {
    if (json.getValue("reconnectAttempts") instanceof Number) {
      obj.setReconnectAttempts(((Number)json.getValue("reconnectAttempts")).intValue());
    }
    if (json.getValue("reconnectInterval") instanceof Number) {
      obj.setReconnectInterval(((Number)json.getValue("reconnectInterval")).longValue());
    }
  }

  public static void toJson(NetClientOptions obj, JsonObject json) {
    json.put("reconnectAttempts", obj.getReconnectAttempts());
    json.put("reconnectInterval", obj.getReconnectInterval());
  }
}