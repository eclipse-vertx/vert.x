package io.vertx.core.http;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.core.http.HttpConnectOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.http.HttpConnectOptions} original class using Vert.x codegen.
 */
public class HttpConnectOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, HttpConnectOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "proxyOptions":
          if (member.getValue() instanceof JsonObject) {
            obj.setProxyOptions(new io.vertx.core.net.ProxyOptions((io.vertx.core.json.JsonObject)member.getValue()));
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
        case "ssl":
          if (member.getValue() instanceof Boolean) {
            obj.setSsl((Boolean)member.getValue());
          }
          break;
        case "sslOptions":
          if (member.getValue() instanceof JsonObject) {
            obj.setSslOptions(new io.vertx.core.net.ClientSSLOptions((io.vertx.core.json.JsonObject)member.getValue()));
          }
          break;
        case "connectTimeout":
          if (member.getValue() instanceof Number) {
            obj.setConnectTimeout(((Number)member.getValue()).longValue());
          }
          break;
      }
    }
  }

   static void toJson(HttpConnectOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(HttpConnectOptions obj, java.util.Map<String, Object> json) {
    if (obj.getProxyOptions() != null) {
      json.put("proxyOptions", obj.getProxyOptions().toJson());
    }
    if (obj.getHost() != null) {
      json.put("host", obj.getHost());
    }
    if (obj.getPort() != null) {
      json.put("port", obj.getPort());
    }
    if (obj.isSsl() != null) {
      json.put("ssl", obj.isSsl());
    }
    if (obj.getSslOptions() != null) {
      json.put("sslOptions", obj.getSslOptions().toJson());
    }
    json.put("connectTimeout", obj.getConnectTimeout());
  }
}
