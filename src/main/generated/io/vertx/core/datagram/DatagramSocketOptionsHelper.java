package io.vertx.core.datagram;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

public class DatagramSocketOptionsHelper {

  public static void fromJson(JsonObject json, DatagramSocketOptions obj) {
    if (json.getValue("broadcast") instanceof Boolean) {
      obj.setBroadcast((Boolean)json.getValue("broadcast"));
    }
    if (json.getValue("ipV6") instanceof Boolean) {
      obj.setIpV6((Boolean)json.getValue("ipV6"));
    }
    if (json.getValue("loopbackModeDisabled") instanceof Boolean) {
      obj.setLoopbackModeDisabled((Boolean)json.getValue("loopbackModeDisabled"));
    }
    if (json.getValue("multicastNetworkInterface") instanceof String) {
      obj.setMulticastNetworkInterface((String)json.getValue("multicastNetworkInterface"));
    }
    if (json.getValue("multicastTimeToLive") instanceof Number) {
      obj.setMulticastTimeToLive(((Number)json.getValue("multicastTimeToLive")).intValue());
    }
  }

  public static void toJson(DatagramSocketOptions obj, JsonObject json) {
    json.put("broadcast", obj.isBroadcast());
    json.put("ipV6", obj.isIpV6());
    json.put("loopbackModeDisabled", obj.isLoopbackModeDisabled());
    if (obj.getMulticastNetworkInterface() != null) {
      json.put("multicastNetworkInterface", obj.getMulticastNetworkInterface());
    }
    json.put("multicastTimeToLive", obj.getMulticastTimeToLive());
  }
}