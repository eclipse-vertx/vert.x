package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import io.vertx.core.spi.json.JsonCodec;

/**
 * Converter and Codec for {@link io.vertx.core.net.ProxyOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.ProxyOptions} original class using Vert.x codegen.
 */
public class ProxyOptionsConverter implements JsonCodec<ProxyOptions, JsonObject> {

  public static final ProxyOptionsConverter INSTANCE = new ProxyOptionsConverter();

  @Override public JsonObject encode(ProxyOptions value) { return (value != null) ? value.toJson() : null; }

  @Override public ProxyOptions decode(JsonObject value) { return (value != null) ? new ProxyOptions(value) : null; }

  @Override public Class<ProxyOptions> getTargetClass() { return ProxyOptions.class; }

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, ProxyOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "host":
          if (member.getValue() instanceof String) {
            obj.setHost((String)member.getValue());
          }
          break;
        case "password":
          if (member.getValue() instanceof String) {
            obj.setPassword((String)member.getValue());
          }
          break;
        case "port":
          if (member.getValue() instanceof Number) {
            obj.setPort(((Number)member.getValue()).intValue());
          }
          break;
        case "type":
          if (member.getValue() instanceof String) {
            obj.setType(io.vertx.core.net.ProxyType.valueOf((String)member.getValue()));
          }
          break;
        case "username":
          if (member.getValue() instanceof String) {
            obj.setUsername((String)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(ProxyOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(ProxyOptions obj, java.util.Map<String, Object> json) {
    if (obj.getHost() != null) {
      json.put("host", obj.getHost());
    }
    if (obj.getPassword() != null) {
      json.put("password", obj.getPassword());
    }
    json.put("port", obj.getPort());
    if (obj.getType() != null) {
      json.put("type", obj.getType().name());
    }
    if (obj.getUsername() != null) {
      json.put("username", obj.getUsername());
    }
  }
}
