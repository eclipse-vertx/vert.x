package io.vertx.core.http;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx.core.http.RequestOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.http.RequestOptions} original class using Vert.x codegen.
 */
public class RequestOptionsConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, RequestOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "uri":
          if (member.getValue() instanceof String) {
            obj.setURI((String)member.getValue());
          }
          break;
        case "followRedirects":
          if (member.getValue() instanceof Boolean) {
            obj.setFollowRedirects((Boolean)member.getValue());
          }
          break;
        case "timeout":
          if (member.getValue() instanceof Number) {
            obj.setTimeout(((Number)member.getValue()).longValue());
          }
          break;
        case "idleTimeout":
          if (member.getValue() instanceof Number) {
            obj.setIdleTimeout(((Number)member.getValue()).longValue());
          }
          break;
        case "absoluteURI":
          if (member.getValue() instanceof String) {
            obj.setAbsoluteURI((String)member.getValue());
          }
          break;
        case "traceOperation":
          if (member.getValue() instanceof String) {
            obj.setTraceOperation((String)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(RequestOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(RequestOptions obj, java.util.Map<String, Object> json) {
    if (obj.getURI() != null) {
      json.put("uri", obj.getURI());
    }
    if (obj.getFollowRedirects() != null) {
      json.put("followRedirects", obj.getFollowRedirects());
    }
    json.put("timeout", obj.getTimeout());
    json.put("idleTimeout", obj.getIdleTimeout());
    if (obj.getTraceOperation() != null) {
      json.put("traceOperation", obj.getTraceOperation());
    }
  }
}
