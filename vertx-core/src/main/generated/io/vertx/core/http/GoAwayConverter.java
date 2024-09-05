package io.vertx.core.http;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx.core.http.GoAway}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.http.GoAway} original class using Vert.x codegen.
 */
public class GoAwayConverter {

  private static final Base64.Decoder BASE64_DECODER = Base64.getUrlDecoder();
  private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, GoAway obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
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
        case "debugData":
          if (member.getValue() instanceof String) {
            obj.setDebugData(io.vertx.core.buffer.Buffer.buffer(BASE64_DECODER.decode((String)member.getValue())));
          }
          break;
      }
    }
  }

   static void toJson(GoAway obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(GoAway obj, java.util.Map<String, Object> json) {
    json.put("errorCode", obj.getErrorCode());
    json.put("lastStreamId", obj.getLastStreamId());
    if (obj.getDebugData() != null) {
      json.put("debugData", BASE64_ENCODER.encodeToString(obj.getDebugData().getBytes()));
    }
  }
}
