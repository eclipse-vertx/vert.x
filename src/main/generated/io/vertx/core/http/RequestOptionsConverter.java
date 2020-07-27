package io.vertx.core.http;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.core.http.RequestOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.http.RequestOptions} original class using Vert.x codegen.
 */
public class RequestOptionsConverter {


  public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, RequestOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "absoluteURI":
          if (member.getValue() instanceof String) {
            obj.setAbsoluteURI((String)member.getValue());
          }
          break;
        case "followRedirects":
          if (member.getValue() instanceof Boolean) {
            obj.setFollowRedirects((Boolean)member.getValue());
          }
          break;
        case "headers":
          if (member.getValue() instanceof JsonObject) {
            ((Iterable<java.util.Map.Entry<String, Object>>)member.getValue()).forEach(entry -> {
              if (entry.getValue() instanceof String)
                obj.addHeader(entry.getKey(), (String)entry.getValue());
            });
          }
          break;
        case "host":
          if (member.getValue() instanceof String) {
            obj.setHost((String)member.getValue());
          }
          break;
        case "method":
          if (member.getValue() instanceof String) {
            obj.setMethod(new io.vertx.core.http.HttpMethod((java.lang.String)member.getValue()));
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
        case "timeout":
          if (member.getValue() instanceof Number) {
            obj.setTimeout(((Number)member.getValue()).longValue());
          }
          break;
        case "uri":
          if (member.getValue() instanceof String) {
            obj.setURI((String)member.getValue());
          }
          break;
      }
    }
  }

  public static void toJson(RequestOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

  public static void toJson(RequestOptions obj, java.util.Map<String, Object> json) {
    if (obj.getFollowRedirects() != null) {
      json.put("followRedirects", obj.getFollowRedirects());
    }
    if (obj.getHost() != null) {
      json.put("host", obj.getHost());
    }
    if (obj.getMethod() != null) {
      json.put("method", obj.getMethod().toJson());
    }
    if (obj.getPort() != null) {
      json.put("port", obj.getPort());
    }
    if (obj.isSsl() != null) {
      json.put("ssl", obj.isSsl());
    }
    json.put("timeout", obj.getTimeout());
    if (obj.getURI() != null) {
      json.put("uri", obj.getURI());
    }
  }
}
