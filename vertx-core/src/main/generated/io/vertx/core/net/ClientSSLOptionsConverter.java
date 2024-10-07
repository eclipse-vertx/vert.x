package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.core.net.ClientSSLOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.ClientSSLOptions} original class using Vert.x codegen.
 */
public class ClientSSLOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, ClientSSLOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "hostnameVerificationAlgorithm":
          if (member.getValue() instanceof String) {
            obj.setHostnameVerificationAlgorithm((String)member.getValue());
          }
          break;
        case "trustAll":
          if (member.getValue() instanceof Boolean) {
            obj.setTrustAll((Boolean)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(ClientSSLOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(ClientSSLOptions obj, java.util.Map<String, Object> json) {
    if (obj.getHostnameVerificationAlgorithm() != null) {
      json.put("hostnameVerificationAlgorithm", obj.getHostnameVerificationAlgorithm());
    }
    json.put("trustAll", obj.isTrustAll());
  }
}
