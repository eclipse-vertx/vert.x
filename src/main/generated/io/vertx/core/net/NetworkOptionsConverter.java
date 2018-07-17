package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter for {@link io.vertx.core.net.NetworkOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.NetworkOptions} original class using Vert.x codegen.
 */
 class NetworkOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, NetworkOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "logActivity":
          if (member.getValue() instanceof Boolean) {
            obj.setLogActivity((Boolean)member.getValue());
          }
          break;
        case "receiveBufferSize":
          if (member.getValue() instanceof Number) {
            obj.setReceiveBufferSize(((Number)member.getValue()).intValue());
          }
          break;
        case "reuseAddress":
          if (member.getValue() instanceof Boolean) {
            obj.setReuseAddress((Boolean)member.getValue());
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
        case "trafficClass":
          if (member.getValue() instanceof Number) {
            obj.setTrafficClass(((Number)member.getValue()).intValue());
          }
          break;
      }
    }
  }

   static void toJson(NetworkOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(NetworkOptions obj, java.util.Map<String, Object> json) {
    json.put("logActivity", obj.getLogActivity());
    json.put("receiveBufferSize", obj.getReceiveBufferSize());
    json.put("reuseAddress", obj.isReuseAddress());
    json.put("reusePort", obj.isReusePort());
    json.put("sendBufferSize", obj.getSendBufferSize());
    json.put("trafficClass", obj.getTrafficClass());
  }
}
