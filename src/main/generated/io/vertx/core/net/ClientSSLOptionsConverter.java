package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx.core.net.ClientSSLOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.ClientSSLOptions} original class using Vert.x codegen.
 */
public class ClientSSLOptionsConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

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
