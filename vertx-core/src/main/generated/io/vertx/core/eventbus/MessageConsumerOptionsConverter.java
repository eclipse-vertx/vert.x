package io.vertx.core.eventbus;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.core.eventbus.MessageConsumerOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.eventbus.MessageConsumerOptions} original class using Vert.x codegen.
 */
public class MessageConsumerOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, MessageConsumerOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "address":
          if (member.getValue() instanceof String) {
            obj.setAddress((String)member.getValue());
          }
          break;
        case "localOnly":
          if (member.getValue() instanceof Boolean) {
            obj.setLocalOnly((Boolean)member.getValue());
          }
          break;
        case "maxBufferedMessages":
          if (member.getValue() instanceof Number) {
            obj.setMaxBufferedMessages(((Number)member.getValue()).intValue());
          }
          break;
      }
    }
  }

   static void toJson(MessageConsumerOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(MessageConsumerOptions obj, java.util.Map<String, Object> json) {
    if (obj.getAddress() != null) {
      json.put("address", obj.getAddress());
    }
    json.put("localOnly", obj.isLocalOnly());
    json.put("maxBufferedMessages", obj.getMaxBufferedMessages());
  }
}
