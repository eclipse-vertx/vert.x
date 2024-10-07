package io.vertx.core.eventbus;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.core.eventbus.EventBusOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.eventbus.EventBusOptions} original class using Vert.x codegen.
 */
public class EventBusOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, EventBusOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "clientAuth":
          if (member.getValue() instanceof String) {
            obj.setClientAuth(io.vertx.core.http.ClientAuth.valueOf((String)member.getValue()));
          }
          break;
        case "acceptBacklog":
          if (member.getValue() instanceof Number) {
            obj.setAcceptBacklog(((Number)member.getValue()).intValue());
          }
          break;
        case "host":
          if (member.getValue() instanceof String) {
            obj.setHost((String)member.getValue());
          }
          break;
        case "port":
          if (member.getValue() instanceof Number) {
            obj.setPort(((Number)member.getValue()).intValue());
          }
          break;
        case "reconnectAttempts":
          if (member.getValue() instanceof Number) {
            obj.setReconnectAttempts(((Number)member.getValue()).intValue());
          }
          break;
        case "reconnectInterval":
          if (member.getValue() instanceof Number) {
            obj.setReconnectInterval(((Number)member.getValue()).longValue());
          }
          break;
        case "trustAll":
          if (member.getValue() instanceof Boolean) {
            obj.setTrustAll((Boolean)member.getValue());
          }
          break;
        case "connectTimeout":
          if (member.getValue() instanceof Number) {
            obj.setConnectTimeout(((Number)member.getValue()).intValue());
          }
          break;
        case "clusterPingInterval":
          if (member.getValue() instanceof Number) {
            obj.setClusterPingInterval(((Number)member.getValue()).longValue());
          }
          break;
        case "clusterPingReplyInterval":
          if (member.getValue() instanceof Number) {
            obj.setClusterPingReplyInterval(((Number)member.getValue()).longValue());
          }
          break;
        case "clusterPublicHost":
          if (member.getValue() instanceof String) {
            obj.setClusterPublicHost((String)member.getValue());
          }
          break;
        case "clusterPublicPort":
          if (member.getValue() instanceof Number) {
            obj.setClusterPublicPort(((Number)member.getValue()).intValue());
          }
          break;
        case "clusterNodeMetadata":
          if (member.getValue() instanceof JsonObject) {
            obj.setClusterNodeMetadata(((JsonObject)member.getValue()).copy());
          }
          break;
      }
    }
  }

   static void toJson(EventBusOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(EventBusOptions obj, java.util.Map<String, Object> json) {
    if (obj.getClientAuth() != null) {
      json.put("clientAuth", obj.getClientAuth().name());
    }
    json.put("acceptBacklog", obj.getAcceptBacklog());
    if (obj.getHost() != null) {
      json.put("host", obj.getHost());
    }
    json.put("port", obj.getPort());
    json.put("reconnectAttempts", obj.getReconnectAttempts());
    json.put("reconnectInterval", obj.getReconnectInterval());
    json.put("trustAll", obj.isTrustAll());
    json.put("connectTimeout", obj.getConnectTimeout());
    json.put("clusterPingInterval", obj.getClusterPingInterval());
    json.put("clusterPingReplyInterval", obj.getClusterPingReplyInterval());
    if (obj.getClusterPublicHost() != null) {
      json.put("clusterPublicHost", obj.getClusterPublicHost());
    }
    json.put("clusterPublicPort", obj.getClusterPublicPort());
    if (obj.getClusterNodeMetadata() != null) {
      json.put("clusterNodeMetadata", obj.getClusterNodeMetadata());
    }
  }
}
