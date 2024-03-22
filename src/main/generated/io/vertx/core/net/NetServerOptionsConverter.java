package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx.core.net.NetServerOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.NetServerOptions} original class using Vert.x codegen.
 */
public class NetServerOptionsConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, NetServerOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "acceptBacklog":
          if (member.getValue() instanceof Number) {
            obj.setAcceptBacklog(((Number)member.getValue()).intValue());
          }
          break;
        case "clientAuth":
          if (member.getValue() instanceof String) {
            obj.setClientAuth(io.vertx.core.http.ClientAuth.valueOf((String)member.getValue()));
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
        case "registerWriteHandler":
          if (member.getValue() instanceof Boolean) {
            obj.setRegisterWriteHandler((Boolean)member.getValue());
          }
          break;
        case "sni":
          if (member.getValue() instanceof Boolean) {
            obj.setSni((Boolean)member.getValue());
          }
          break;
        case "tcpKeepAliveCount":
          if (member.getValue() instanceof Number) {
            obj.setTcpKeepAliveCount(((Number)member.getValue()).intValue());
          }
          break;
        case "tcpKeepAliveIdleSeconds":
          if (member.getValue() instanceof Number) {
            obj.setTcpKeepAliveIdleSeconds(((Number)member.getValue()).intValue());
          }
          break;
        case "tcpKeepAliveIntervalSeconds":
          if (member.getValue() instanceof Number) {
            obj.setTcpKeepAliveIntervalSeconds(((Number)member.getValue()).intValue());
          }
          break;
        case "trafficShapingOptions":
          if (member.getValue() instanceof JsonObject) {
            obj.setTrafficShapingOptions(new io.vertx.core.net.TrafficShapingOptions((io.vertx.core.json.JsonObject)member.getValue()));
          }
          break;
        case "useProxyProtocol":
          if (member.getValue() instanceof Boolean) {
            obj.setUseProxyProtocol((Boolean)member.getValue());
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
    if (obj.getClientAuth() != null) {
      json.put("clientAuth", obj.getClientAuth().name());
    }
    if (obj.getHost() != null) {
      json.put("host", obj.getHost());
    }
    json.put("port", obj.getPort());
    json.put("proxyProtocolTimeout", obj.getProxyProtocolTimeout());
    if (obj.getProxyProtocolTimeoutUnit() != null) {
      json.put("proxyProtocolTimeoutUnit", obj.getProxyProtocolTimeoutUnit().name());
    }
    json.put("registerWriteHandler", obj.isRegisterWriteHandler());
    json.put("sni", obj.isSni());
    json.put("tcpKeepAliveCount", obj.getTcpKeepAliveCount());
    json.put("tcpKeepAliveIdleSeconds", obj.getTcpKeepAliveIdleSeconds());
    json.put("tcpKeepAliveIntervalSeconds", obj.getTcpKeepAliveIntervalSeconds());
    if (obj.getTrafficShapingOptions() != null) {
      json.put("trafficShapingOptions", obj.getTrafficShapingOptions().toJson());
    }
    json.put("useProxyProtocol", obj.isUseProxyProtocol());
  }
}
