package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter for {@link io.vertx.core.net.PemTrustOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.PemTrustOptions} original class using Vert.x codegen.
 */
 class PemTrustOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, PemTrustOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "certPaths":
          if (member.getValue() instanceof JsonArray) {
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof String)
                obj.addCertPath((String)item);
            });
          }
          break;
        case "certValues":
          if (member.getValue() instanceof JsonArray) {
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof String)
                obj.addCertValue(base64Decode((String)item));
            });
          }
          break;
      }
    }
  }

  private static final java.util.concurrent.atomic.AtomicBoolean base64WarningLogged = new java.util.concurrent.atomic.AtomicBoolean();

  private static io.vertx.core.buffer.Buffer base64Decode(String value) {
    try {
      return io.vertx.core.buffer.Buffer.buffer(java.util.Base64.getUrlDecoder().decode(value));
    } catch (IllegalArgumentException e) {
      io.vertx.core.buffer.Buffer result = io.vertx.core.buffer.Buffer.buffer(java.util.Base64.getDecoder().decode(value));
      if (base64WarningLogged.compareAndSet(false, true)) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        pw.println("Failed to decode a PemTrustOptions value with base64url encoding. Used the base64 fallback.");
        e.printStackTrace(pw);
        pw.close();
        System.err.print(sw.toString());
      }
      return result;
    }
  }

   static void toJson(PemTrustOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(PemTrustOptions obj, java.util.Map<String, Object> json) {
    if (obj.getCertPaths() != null) {
      JsonArray array = new JsonArray();
      obj.getCertPaths().forEach(item -> array.add(item));
      json.put("certPaths", array);
    }
    if (obj.getCertValues() != null) {
      JsonArray array = new JsonArray();
      obj.getCertValues().forEach(item -> array.add(java.util.Base64.getUrlEncoder().encodeToString(item.getBytes())));
      json.put("certValues", array);
    }
  }
}
