package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx.core.net.PemKeyCertOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.PemKeyCertOptions} original class using Vert.x codegen.
 */
public class PemKeyCertOptionsConverter {

  private static final Base64.Decoder BASE64_DECODER = Base64.getUrlDecoder();
  private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, PemKeyCertOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
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
            obj.setKeyValue(io.vertx.core.buffer.Buffer.buffer(BASE64_DECODER.decode((String)member.getValue())));
          }
          break;
        case "keyValues":
          if (member.getValue() instanceof JsonArray) {
            java.util.ArrayList<io.vertx.core.buffer.Buffer> list =  new java.util.ArrayList<>();
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof String)
                list.add(io.vertx.core.buffer.Buffer.buffer(BASE64_DECODER.decode((String)item)));
            });
            obj.setKeyValues(list);
          }
          break;
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
            obj.setCertValue(io.vertx.core.buffer.Buffer.buffer(BASE64_DECODER.decode((String)member.getValue())));
          }
          break;
        case "certValues":
          if (member.getValue() instanceof JsonArray) {
            java.util.ArrayList<io.vertx.core.buffer.Buffer> list =  new java.util.ArrayList<>();
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof String)
                list.add(io.vertx.core.buffer.Buffer.buffer(BASE64_DECODER.decode((String)item)));
            });
            obj.setCertValues(list);
          }
          break;
      }
    }
  }

   static void toJson(PemKeyCertOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(PemKeyCertOptions obj, java.util.Map<String, Object> json) {
    if (obj.getKeyPaths() != null) {
      JsonArray array = new JsonArray();
      obj.getKeyPaths().forEach(item -> array.add(item));
      json.put("keyPaths", array);
    }
    if (obj.getKeyValues() != null) {
      JsonArray array = new JsonArray();
      obj.getKeyValues().forEach(item -> array.add(BASE64_ENCODER.encodeToString(item.getBytes())));
      json.put("keyValues", array);
    }
    if (obj.getCertPaths() != null) {
      JsonArray array = new JsonArray();
      obj.getCertPaths().forEach(item -> array.add(item));
      json.put("certPaths", array);
    }
    if (obj.getCertValues() != null) {
      JsonArray array = new JsonArray();
      obj.getCertValues().forEach(item -> array.add(BASE64_ENCODER.encodeToString(item.getBytes())));
      json.put("certValues", array);
    }
  }
}
