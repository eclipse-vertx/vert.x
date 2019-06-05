package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import io.vertx.core.spi.json.JsonCodec;

/**
 * Converter and Codec for {@link io.vertx.core.net.NetServerOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.NetServerOptions} original class using Vert.x codegen.
 */
public class NetServerOptionsConverter implements JsonCodec<NetServerOptions, JsonObject> {

  public static final NetServerOptionsConverter INSTANCE = new NetServerOptionsConverter();

  @Override public JsonObject encode(NetServerOptions value) { return (value != null) ? value.toJson() : null; }

  @Override public NetServerOptions decode(JsonObject value) { return (value != null) ? new NetServerOptions(value) : null; }

  @Override public Class<NetServerOptions> getTargetClass() { return NetServerOptions.class; }

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
        case "sni":
          if (member.getValue() instanceof Boolean) {
            obj.setSni((Boolean)member.getValue());
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
    json.put("sni", obj.isSni());
  }
}
