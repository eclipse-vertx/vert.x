package io.vertx.core.eventbus;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

public class DeliveryOptionsHelper {

  public static void fromJson(JsonObject json, DeliveryOptions obj) {
    if (json.getValue("codecName") instanceof String) {
      obj.setCodecName((String)json.getValue("codecName"));
    }
    if (json.getValue("sendTimeout") instanceof Number) {
      obj.setSendTimeout(((Number)json.getValue("sendTimeout")).longValue());
    }
  }

  public static void toJson(DeliveryOptions obj, JsonObject json) {
    if (obj.getCodecName() != null) {
      json.put("codecName", obj.getCodecName());
    }
    json.put("sendTimeout", obj.getSendTimeout());
  }
}