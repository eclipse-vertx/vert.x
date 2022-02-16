package io.vertx.core.dns;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx.core.dns.DnsClientOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.dns.DnsClientOptions} original class using Vert.x codegen.
 */
public class DnsClientOptionsConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

  public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, DnsClientOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "activityLogFormat":
          if (member.getValue() instanceof String) {
            obj.setActivityLogFormat(io.netty.handler.logging.ByteBufFormat.valueOf((String)member.getValue()));
          }
          break;
        case "host":
          if (member.getValue() instanceof String) {
            obj.setHost((String)member.getValue());
          }
          break;
        case "logActivity":
          if (member.getValue() instanceof Boolean) {
            obj.setLogActivity((Boolean)member.getValue());
          }
          break;
        case "port":
          if (member.getValue() instanceof Number) {
            obj.setPort(((Number)member.getValue()).intValue());
          }
          break;
        case "queryTimeout":
          if (member.getValue() instanceof Number) {
            obj.setQueryTimeout(((Number)member.getValue()).longValue());
          }
          break;
        case "recursionDesired":
          if (member.getValue() instanceof Boolean) {
            obj.setRecursionDesired((Boolean)member.getValue());
          }
          break;
      }
    }
  }

  public static void toJson(DnsClientOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

  public static void toJson(DnsClientOptions obj, java.util.Map<String, Object> json) {
    if (obj.getActivityLogFormat() != null) {
      json.put("activityLogFormat", obj.getActivityLogFormat().name());
    }
    if (obj.getHost() != null) {
      json.put("host", obj.getHost());
    }
    json.put("logActivity", obj.getLogActivity());
    json.put("port", obj.getPort());
    json.put("queryTimeout", obj.getQueryTimeout());
    json.put("recursionDesired", obj.isRecursionDesired());
  }
}
