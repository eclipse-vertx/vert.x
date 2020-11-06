package io.vertx.core.http;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.core.http.GoAway}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.http.GoAway} original class using Vert.x codegen.
 */
public class GoAwayConverter {


   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, GoAway obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "debugData":
          if (member.getValue() instanceof String) {
            obj.setDebugData(io.vertx.core.buffer.Buffer.buffer(JsonUtil.BASE64_DECODER.decode((String)member.getValue())));
          }
          break;
        case "errorCode":
          if (member.getValue() instanceof Number) {
            obj.setErrorCode(((Number)member.getValue()).longValue());
          }
          break;
        case "lastStreamId":
          if (member.getValue() instanceof Number) {
            obj.setLastStreamId(((Number)member.getValue()).intValue());
          }
          break;
      }
    }
  }

   static void toJson(GoAway obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(GoAway obj, java.util.Map<String, Object> json) {
    if (obj.getDebugData() != null) {
      json.put("debugData", JsonUtil.BASE64_ENCODER.encodeToString(obj.getDebugData().getBytes()));
    }
    json.put("errorCode", obj.getErrorCode());
    json.put("lastStreamId", obj.getLastStreamId());
  }
}
