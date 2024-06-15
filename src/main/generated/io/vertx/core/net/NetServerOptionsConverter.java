package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx.core.net.NetServerOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.NetServerOptions} original class using Vert.x codegen.
 */
public class NetServerOptionsConverter {

  private static final Base64.Decoder BASE64_DECODER = Base64.getUrlDecoder();
  private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, NetServerOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "acceptBacklog":
          if (member.getValue() instanceof Number) {
            obj.setAcceptBacklog(((Number)member.getValue()).intValue());
          }
          break;
        case "port":
          if (member.getValue() instanceof Number) {
            obj.setPort(((Number)member.getValue()).intValue());
          }
          break;
        case "host":
          if (member.getValue() instanceof String) {
            obj.setHost((String)member.getValue());
          }
          break;
        case "clientAuth":
          if (member.getValue() instanceof String) {
            obj.setClientAuth(io.vertx.core.http.ClientAuth.valueOf((String)member.getValue()));
          }
          break;
        case "sni":
          if (member.getValue() instanceof Boolean) {
            obj.setSni((Boolean)member.getValue());
          }
          break;
        case "useProxyProtocol":
          if (member.getValue() instanceof Boolean) {
            obj.setUseProxyProtocol((Boolean)member.getValue());
          }
          break;
        case "proxyProtocolTimeout":
          if (member.getValue() instanceof Number) {
            obj.setProxyProtocolTimeout(((Number)member.getValue()).longValue());
          }
          break;
        case "proxyProtocolTimeoutUnit":
          if (member.getValue() instanceof String) {
            obj.setProxyProtocolTimeoutUnit(java.util.concurrent.TimeUnit.valueOf((String)member.getValue()));
          }
          break;
        case "trafficShapingOptions":
          if (member.getValue() instanceof JsonObject) {
            obj.setTrafficShapingOptions(new io.vertx.core.net.TrafficShapingOptions((io.vertx.core.json.JsonObject)member.getValue()));
          }
          break;
        case "registerWriteHandler":
          if (member.getValue() instanceof Boolean) {
            obj.setRegisterWriteHandler((Boolean)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(NetServerOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(NetServerOptions obj, java.util.Map<String, Object> json) {
    json.put("acceptBacklog", obj.getAcceptBacklog());
    json.put("port", obj.getPort());
    if (obj.getHost() != null) {
      json.put("host", obj.getHost());
    }
    if (obj.getClientAuth() != null) {
      json.put("clientAuth", obj.getClientAuth().name());
    }
    json.put("sni", obj.isSni());
    json.put("useProxyProtocol", obj.isUseProxyProtocol());
    json.put("proxyProtocolTimeout", obj.getProxyProtocolTimeout());
    if (obj.getProxyProtocolTimeoutUnit() != null) {
      json.put("proxyProtocolTimeoutUnit", obj.getProxyProtocolTimeoutUnit().name());
    }
    if (obj.getTrafficShapingOptions() != null) {
      json.put("trafficShapingOptions", obj.getTrafficShapingOptions().toJson());
    }
    json.put("registerWriteHandler", obj.isRegisterWriteHandler());
  }
}
