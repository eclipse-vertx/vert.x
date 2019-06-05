package io.vertx.core.dns;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import io.vertx.core.spi.json.JsonCodec;

/**
 * Converter and Codec for {@link io.vertx.core.dns.DnsClientOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.dns.DnsClientOptions} original class using Vert.x codegen.
 */
public class DnsClientOptionsConverter implements JsonCodec<DnsClientOptions, JsonObject> {

  public static final DnsClientOptionsConverter INSTANCE = new DnsClientOptionsConverter();

  @Override public JsonObject encode(DnsClientOptions value) { return (value != null) ? value.toJson() : null; }

  @Override public DnsClientOptions decode(JsonObject value) { return (value != null) ? new DnsClientOptions(value) : null; }

  @Override public Class<DnsClientOptions> getTargetClass() { return DnsClientOptions.class; }

  public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, DnsClientOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
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
    if (obj.getHost() != null) {
      json.put("host", obj.getHost());
    }
    json.put("logActivity", obj.getLogActivity());
    json.put("port", obj.getPort());
    json.put("queryTimeout", obj.getQueryTimeout());
    json.put("recursionDesired", obj.isRecursionDesired());
  }
}
