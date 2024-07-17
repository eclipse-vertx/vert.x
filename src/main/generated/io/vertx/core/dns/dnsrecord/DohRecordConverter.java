package io.vertx.core.dns.dnsrecord;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx.core.dns.dnsrecord.DohRecord}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.dns.dnsrecord.DohRecord} original class using Vert.x codegen.
 */
public class DohRecordConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, DohRecord obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "ad":
          if (member.getValue() instanceof Boolean) {
            obj.setAd((Boolean)member.getValue());
          }
          break;
        case "additional":
          if (member.getValue() instanceof JsonArray) {
            java.util.ArrayList<io.vertx.core.dns.dnsrecord.DohResourceRecord> list =  new java.util.ArrayList<>();
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof JsonObject)
                list.add(new io.vertx.core.dns.dnsrecord.DohResourceRecord((io.vertx.core.json.JsonObject)item));
            });
            obj.setAdditional(list);
          }
          break;
        case "answer":
          if (member.getValue() instanceof JsonArray) {
            java.util.ArrayList<io.vertx.core.dns.dnsrecord.DohResourceRecord> list =  new java.util.ArrayList<>();
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof JsonObject)
                list.add(new io.vertx.core.dns.dnsrecord.DohResourceRecord((io.vertx.core.json.JsonObject)item));
            });
            obj.setAnswer(list);
          }
          break;
        case "authority":
          if (member.getValue() instanceof JsonArray) {
            java.util.ArrayList<io.vertx.core.dns.dnsrecord.DohResourceRecord> list =  new java.util.ArrayList<>();
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof JsonObject)
                list.add(new io.vertx.core.dns.dnsrecord.DohResourceRecord((io.vertx.core.json.JsonObject)item));
            });
            obj.setAuthority(list);
          }
          break;
        case "cd":
          if (member.getValue() instanceof Boolean) {
            obj.setCd((Boolean)member.getValue());
          }
          break;
        case "question":
          if (member.getValue() instanceof JsonArray) {
            java.util.ArrayList<io.vertx.core.dns.dnsrecord.Question> list =  new java.util.ArrayList<>();
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof JsonObject)
                list.add(new io.vertx.core.dns.dnsrecord.Question((io.vertx.core.json.JsonObject)item));
            });
            obj.setQuestion(list);
          }
          break;
        case "ra":
          if (member.getValue() instanceof Boolean) {
            obj.setRa((Boolean)member.getValue());
          }
          break;
        case "rd":
          if (member.getValue() instanceof Boolean) {
            obj.setRd((Boolean)member.getValue());
          }
          break;
        case "status":
          if (member.getValue() instanceof Number) {
            obj.setStatus(((Number)member.getValue()).intValue());
          }
          break;
        case "tc":
          if (member.getValue() instanceof Boolean) {
            obj.setTc((Boolean)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(DohRecord obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(DohRecord obj, java.util.Map<String, Object> json) {
    json.put("ad", obj.isAd());
    if (obj.getAdditional() != null) {
      JsonArray array = new JsonArray();
      obj.getAdditional().forEach(item -> array.add(item.toJson()));
      json.put("additional", array);
    }
    if (obj.getAnswer() != null) {
      JsonArray array = new JsonArray();
      obj.getAnswer().forEach(item -> array.add(item.toJson()));
      json.put("answer", array);
    }
    if (obj.getAuthority() != null) {
      JsonArray array = new JsonArray();
      obj.getAuthority().forEach(item -> array.add(item.toJson()));
      json.put("authority", array);
    }
    json.put("cd", obj.isCd());
    if (obj.getQuestion() != null) {
      JsonArray array = new JsonArray();
      obj.getQuestion().forEach(item -> array.add(item.toJson()));
      json.put("question", array);
    }
    json.put("ra", obj.isRa());
    json.put("rd", obj.isRd());
    json.put("status", obj.getStatus());
    json.put("tc", obj.isTc());
  }
}
