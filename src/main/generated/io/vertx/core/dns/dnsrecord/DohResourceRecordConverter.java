package io.vertx.core.dns.dnsrecord;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx.core.dns.dnsrecord.DohResourceRecord}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.dns.dnsrecord.DohResourceRecord} original class using Vert.x codegen.
 */
public class DohResourceRecordConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, DohResourceRecord obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "data":
          if (member.getValue() instanceof String) {
            obj.setData((String)member.getValue());
          }
          break;
        case "name":
          if (member.getValue() instanceof String) {
            obj.setName((String)member.getValue());
          }
          break;
        case "ttl":
          if (member.getValue() instanceof Number) {
            obj.setTtl(((Number)member.getValue()).intValue());
          }
          break;
        case "type":
          if (member.getValue() instanceof Number) {
            obj.setType(((Number)member.getValue()).intValue());
          }
          break;
      }
    }
  }

   static void toJson(DohResourceRecord obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(DohResourceRecord obj, java.util.Map<String, Object> json) {
    if (obj.getData() != null) {
      json.put("data", obj.getData());
    }
    if (obj.getName() != null) {
      json.put("name", obj.getName());
    }
    json.put("ttl", obj.getTtl());
    json.put("type", obj.getType());
  }
}
