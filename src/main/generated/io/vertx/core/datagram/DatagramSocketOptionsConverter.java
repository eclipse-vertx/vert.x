package io.vertx.core.datagram;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.core.datagram.DatagramSocketOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.datagram.DatagramSocketOptions} original class using Vert.x codegen.
 */
public class DatagramSocketOptionsConverter {


   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, DatagramSocketOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "broadcast":
          if (member.getValue() instanceof Boolean) {
            obj.setBroadcast((Boolean)member.getValue());
          }
          break;
        case "ipV6":
          if (member.getValue() instanceof Boolean) {
            obj.setIpV6((Boolean)member.getValue());
          }
          break;
        case "loopbackModeDisabled":
          if (member.getValue() instanceof Boolean) {
            obj.setLoopbackModeDisabled((Boolean)member.getValue());
          }
          break;
        case "multicastNetworkInterface":
          if (member.getValue() instanceof String) {
            obj.setMulticastNetworkInterface((String)member.getValue());
          }
          break;
        case "multicastTimeToLive":
          if (member.getValue() instanceof Number) {
            obj.setMulticastTimeToLive(((Number)member.getValue()).intValue());
          }
          break;
      }
    }
  }

   static void toJson(DatagramSocketOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(DatagramSocketOptions obj, java.util.Map<String, Object> json) {
    json.put("broadcast", obj.isBroadcast());
    json.put("ipV6", obj.isIpV6());
    json.put("loopbackModeDisabled", obj.isLoopbackModeDisabled());
    if (obj.getMulticastNetworkInterface() != null) {
      json.put("multicastNetworkInterface", obj.getMulticastNetworkInterface());
    }
    json.put("multicastTimeToLive", obj.getMulticastTimeToLive());
  }
}
