package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter for {@link io.vertx.core.net.PemKeyCertOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.PemKeyCertOptions} original class using Vert.x codegen.
 */
 class PemKeyCertOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, PemKeyCertOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "certPath":
          if (member.getValue() instanceof String) {
            obj.setCertPath((String)member.getValue());
          }
          break;
        case "certPaths":
          if (member.getValue() instanceof JsonArray) {
            java.util.ArrayList<java.lang.String> list =  new java.util.ArrayList<>();
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof String)
                list.add((String)item);
            });
            obj.setCertPaths(list);
          }
          break;
        case "certValue":
          if (member.getValue() instanceof String) {
            obj.setCertValue(base64Decode((String)member.getValue()));
          }
          break;
        case "certValues":
          if (member.getValue() instanceof JsonArray) {
            java.util.ArrayList<io.vertx.core.buffer.Buffer> list =  new java.util.ArrayList<>();
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof String)
                list.add(base64Decode((String)item));
            });
            obj.setCertValues(list);
          }
          break;
        case "keyPath":
          if (member.getValue() instanceof String) {
            obj.setKeyPath((String)member.getValue());
          }
          break;
        case "keyPaths":
          if (member.getValue() instanceof JsonArray) {
            java.util.ArrayList<java.lang.String> list =  new java.util.ArrayList<>();
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof String)
                list.add((String)item);
            });
            obj.setKeyPaths(list);
          }
          break;
        case "keyValue":
          if (member.getValue() instanceof String) {
            obj.setKeyValue(base64Decode((String)member.getValue()));
          }
          break;
        case "keyValues":
          if (member.getValue() instanceof JsonArray) {
            java.util.ArrayList<io.vertx.core.buffer.Buffer> list =  new java.util.ArrayList<>();
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof String)
                list.add(base64Decode((String)item));
            });
            obj.setKeyValues(list);
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
        pw.println("Failed to decode a PemKeyCertOptions value with base64url encoding. Used the base64 fallback.");
        e.printStackTrace(pw);
        pw.close();
        System.err.print(sw.toString());
      }
      return result;
    }
  }

   static void toJson(PemKeyCertOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(PemKeyCertOptions obj, java.util.Map<String, Object> json) {
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
    if (obj.getKeyPaths() != null) {
      JsonArray array = new JsonArray();
      obj.getKeyPaths().forEach(item -> array.add(item));
      json.put("keyPaths", array);
    }
    if (obj.getKeyValues() != null) {
      JsonArray array = new JsonArray();
      obj.getKeyValues().forEach(item -> array.add(java.util.Base64.getUrlEncoder().encodeToString(item.getBytes())));
      json.put("keyValues", array);
    }
  }
}
