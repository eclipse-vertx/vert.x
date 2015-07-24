package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

public class PemTrustOptionsHelper {

  public static void fromJson(JsonObject json, PemTrustOptions obj) {
    if (json.getValue("certPaths") instanceof JsonArray) {
      json.getJsonArray("certPaths").getList().forEach(item -> {
        if (item instanceof String)
          obj.addCertPath((String)item);
      });
    }
    if (json.getValue("certValues") instanceof JsonArray) {
      json.getJsonArray("certValues").getList().forEach(item -> {
        if (item instanceof String)
          obj.addCertValue(io.vertx.core.buffer.Buffer.buffer(java.util.Base64.getDecoder().decode((String)item)));
      });
    }
  }

  public static void toJson(PemTrustOptions obj, JsonObject json) {
    if (obj.getCertPaths() != null) {
      json.put("certPaths", new JsonArray(
          obj.getCertPaths().
              stream().
              map(item -> item).
              collect(java.util.stream.Collectors.toList())));
    }
    if (obj.getCertValues() != null) {
      json.put("certValues", new JsonArray(
          obj.getCertValues().
              stream().
              map(item -> item.getBytes()).
              collect(java.util.stream.Collectors.toList())));
    }
  }
}