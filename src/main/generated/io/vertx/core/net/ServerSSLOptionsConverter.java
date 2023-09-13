package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx.core.net.ServerSSLOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.ServerSSLOptions} original class using Vert.x codegen.
 */
public class ServerSSLOptionsConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, ServerSSLOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "clientAuth":
          if (member.getValue() instanceof String) {
            obj.setClientAuth(io.vertx.core.http.ClientAuth.valueOf((String)member.getValue()));
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

   static void toJson(ServerSSLOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(ServerSSLOptions obj, java.util.Map<String, Object> json) {
    if (obj.getClientAuth() != null) {
      json.put("clientAuth", obj.getClientAuth().name());
    }
    json.put("sni", obj.isSni());
  }
}
