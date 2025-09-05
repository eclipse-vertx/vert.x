package io.vertx.core.datagram;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * Converter and mapper for {@link io.vertx.core.datagram.DatagramSocketOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.datagram.DatagramSocketOptions} original class using Vert.x codegen.
 */
public class DatagramSocketOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, DatagramSocketOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "reuseAddress":
          if (member.getValue() instanceof Boolean) {
            obj.setReuseAddress((Boolean)member.getValue());
          }
          break;
        case "logActivity":
          if (member.getValue() instanceof Boolean) {
            obj.setLogActivity((Boolean)member.getValue());
          }
          break;
        case "activityLogDataFormat":
          if (member.getValue() instanceof String) {
            obj.setActivityLogDataFormat(io.netty.handler.logging.ByteBufFormat.valueOf((String)member.getValue()));
          }
          break;
        case "reusePort":
          if (member.getValue() instanceof Boolean) {
            obj.setReusePort((Boolean)member.getValue());
          }
          break;
        case "sendBufferSize":
          if (member.getValue() instanceof Number) {
            obj.setSendBufferSize(((Number)member.getValue()).intValue());
          }
          break;
        case "receiveBufferSize":
          if (member.getValue() instanceof Number) {
            obj.setReceiveBufferSize(((Number)member.getValue()).intValue());
          }
          break;
        case "trafficClass":
          if (member.getValue() instanceof Number) {
            obj.setTrafficClass(((Number)member.getValue()).intValue());
          }
          break;
        case "broadcast":
          if (member.getValue() instanceof Boolean) {
            obj.setBroadcast((Boolean)member.getValue());
          }
          break;
        case "loopbackModeDisabled":
          if (member.getValue() instanceof Boolean) {
            obj.setLoopbackModeDisabled((Boolean)member.getValue());
          }
          break;
        case "multicastTimeToLive":
          if (member.getValue() instanceof Number) {
            obj.setMulticastTimeToLive(((Number)member.getValue()).intValue());
          }
          break;
        case "multicastNetworkInterface":
          if (member.getValue() instanceof String) {
            obj.setMulticastNetworkInterface((String)member.getValue());
          }
          break;
        case "ipV6":
          if (member.getValue() instanceof Boolean) {
            obj.setIpV6((Boolean)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(DatagramSocketOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(DatagramSocketOptions obj, java.util.Map<String, Object> json) {
    json.put("reuseAddress", obj.isReuseAddress());
    json.put("logActivity", obj.getLogActivity());
    if (obj.getActivityLogDataFormat() != null) {
      json.put("activityLogDataFormat", obj.getActivityLogDataFormat().name());
    }
    json.put("reusePort", obj.isReusePort());
    json.put("sendBufferSize", obj.getSendBufferSize());
    json.put("receiveBufferSize", obj.getReceiveBufferSize());
    json.put("trafficClass", obj.getTrafficClass());
    json.put("broadcast", obj.isBroadcast());
    json.put("loopbackModeDisabled", obj.isLoopbackModeDisabled());
    json.put("multicastTimeToLive", obj.getMulticastTimeToLive());
    if (obj.getMulticastNetworkInterface() != null) {
      json.put("multicastNetworkInterface", obj.getMulticastNetworkInterface());
    }
    json.put("ipV6", obj.isIpV6());
  }
}
