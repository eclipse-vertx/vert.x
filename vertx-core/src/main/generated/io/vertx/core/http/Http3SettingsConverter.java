package io.vertx.core.http;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * Converter and mapper for {@link io.vertx.core.http.Http3Settings}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.http.Http3Settings} original class using Vert.x codegen.
 */
public class Http3SettingsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, Http3Settings obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "qpackMaxTableCapacity":
          if (member.getValue() instanceof Number) {
            obj.setQpackMaxTableCapacity(((Number)member.getValue()).longValue());
          }
          break;
        case "maxFieldSectionSize":
          if (member.getValue() instanceof Number) {
            obj.setMaxFieldSectionSize(((Number)member.getValue()).longValue());
          }
          break;
        case "qpackMaxBlockedStreams":
          if (member.getValue() instanceof Number) {
            obj.setQpackMaxBlockedStreams(((Number)member.getValue()).longValue());
          }
          break;
        case "enableConnectProtocol":
          if (member.getValue() instanceof Number) {
            obj.setEnableConnectProtocol(((Number)member.getValue()).longValue());
          }
          break;
        case "h3Datagram":
          if (member.getValue() instanceof Number) {
            obj.setH3Datagram(((Number)member.getValue()).longValue());
          }
          break;
        case "enableMetadata":
          if (member.getValue() instanceof Number) {
            obj.setEnableMetadata(((Number)member.getValue()).longValue());
          }
          break;
      }
    }
  }

   static void toJson(Http3Settings obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(Http3Settings obj, java.util.Map<String, Object> json) {
    json.put("qpackMaxTableCapacity", obj.getQpackMaxTableCapacity());
    json.put("maxFieldSectionSize", obj.getMaxFieldSectionSize());
    json.put("qpackMaxBlockedStreams", obj.getQpackMaxBlockedStreams());
    json.put("enableConnectProtocol", obj.getEnableConnectProtocol());
    json.put("h3Datagram", obj.getH3Datagram());
    json.put("enableMetadata", obj.getEnableMetadata());
  }
}
