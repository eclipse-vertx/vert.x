package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import io.vertx.core.spi.json.JsonCodec;

/**
 * Converter and Codec for {@link io.vertx.core.net.NetClientOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.NetClientOptions} original class using Vert.x codegen.
 */
public class NetClientOptionsConverter implements JsonCodec<NetClientOptions, JsonObject> {

  public static final NetClientOptionsConverter INSTANCE = new NetClientOptionsConverter();

  @Override public JsonObject encode(NetClientOptions value) { return (value != null) ? value.toJson() : null; }

  @Override public NetClientOptions decode(JsonObject value) { return (value != null) ? new NetClientOptions(value) : null; }

  @Override public Class<NetClientOptions> getTargetClass() { return NetClientOptions.class; }

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, NetClientOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "hostnameVerificationAlgorithm":
          if (member.getValue() instanceof String) {
            obj.setHostnameVerificationAlgorithm((String)member.getValue());
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
      }
    }
  }

   static void toJson(NetClientOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(NetClientOptions obj, java.util.Map<String, Object> json) {
    if (obj.getHostnameVerificationAlgorithm() != null) {
      json.put("hostnameVerificationAlgorithm", obj.getHostnameVerificationAlgorithm());
    }
    json.put("reconnectAttempts", obj.getReconnectAttempts());
    json.put("reconnectInterval", obj.getReconnectInterval());
  }
}
