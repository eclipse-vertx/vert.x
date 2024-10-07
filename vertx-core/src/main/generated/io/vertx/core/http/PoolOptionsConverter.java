package io.vertx.core.http;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx.core.http.PoolOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.http.PoolOptions} original class using Vert.x codegen.
 */
public class PoolOptionsConverter {

  private static final Base64.Decoder BASE64_DECODER = Base64.getUrlDecoder();
  private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, PoolOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "http1MaxSize":
          if (member.getValue() instanceof Number) {
            obj.setHttp1MaxSize(((Number)member.getValue()).intValue());
          }
          break;
        case "http2MaxSize":
          if (member.getValue() instanceof Number) {
            obj.setHttp2MaxSize(((Number)member.getValue()).intValue());
          }
          break;
        case "cleanerPeriod":
          if (member.getValue() instanceof Number) {
            obj.setCleanerPeriod(((Number)member.getValue()).intValue());
          }
          break;
        case "eventLoopSize":
          if (member.getValue() instanceof Number) {
            obj.setEventLoopSize(((Number)member.getValue()).intValue());
          }
          break;
        case "maxWaitQueueSize":
          if (member.getValue() instanceof Number) {
            obj.setMaxWaitQueueSize(((Number)member.getValue()).intValue());
          }
          break;
      }
    }
  }

   static void toJson(PoolOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(PoolOptions obj, java.util.Map<String, Object> json) {
    json.put("http1MaxSize", obj.getHttp1MaxSize());
    json.put("http2MaxSize", obj.getHttp2MaxSize());
    json.put("cleanerPeriod", obj.getCleanerPeriod());
    json.put("eventLoopSize", obj.getEventLoopSize());
    json.put("maxWaitQueueSize", obj.getMaxWaitQueueSize());
  }
}
