package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

public class NetServerOptionsHelper {

  public static void fromJson(JsonObject json, NetServerOptions obj) {
    if (json.getValue("acceptBacklog") instanceof Number) {
      obj.setAcceptBacklog(((Number)json.getValue("acceptBacklog")).intValue());
    }
    if (json.getValue("clientAuthRequired") instanceof Boolean) {
      obj.setClientAuthRequired((Boolean)json.getValue("clientAuthRequired"));
    }
    if (json.getValue("host") instanceof String) {
      obj.setHost((String)json.getValue("host"));
    }
    if (json.getValue("port") instanceof Number) {
      obj.setPort(((Number)json.getValue("port")).intValue());
    }
  }

  public static void toJson(NetServerOptions obj, JsonObject json) {
    json.put("acceptBacklog", obj.getAcceptBacklog());
    json.put("clientAuthRequired", obj.isClientAuthRequired());
    if (obj.getHost() != null) {
      json.put("host", obj.getHost());
    }
    json.put("port", obj.getPort());
  }
}