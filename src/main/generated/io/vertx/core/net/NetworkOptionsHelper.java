package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

public class NetworkOptionsHelper {

  public static void fromJson(JsonObject json, NetworkOptions obj) {
    if (json.getValue("receiveBufferSize") instanceof Number) {
      obj.setReceiveBufferSize(((Number)json.getValue("receiveBufferSize")).intValue());
    }
    if (json.getValue("reuseAddress") instanceof Boolean) {
      obj.setReuseAddress((Boolean)json.getValue("reuseAddress"));
    }
    if (json.getValue("sendBufferSize") instanceof Number) {
      obj.setSendBufferSize(((Number)json.getValue("sendBufferSize")).intValue());
    }
    if (json.getValue("trafficClass") instanceof Number) {
      obj.setTrafficClass(((Number)json.getValue("trafficClass")).intValue());
    }
  }

  public static void toJson(NetworkOptions obj, JsonObject json) {
    json.put("receiveBufferSize", obj.getReceiveBufferSize());
    json.put("reuseAddress", obj.isReuseAddress());
    json.put("sendBufferSize", obj.getSendBufferSize());
    json.put("trafficClass", obj.getTrafficClass());
  }
}