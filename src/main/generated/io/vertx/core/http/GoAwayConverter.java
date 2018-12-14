package io.vertx.core.http;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter for {@link io.vertx.core.http.GoAway}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.http.GoAway} original class using Vert.x codegen.
 */
 class GoAwayConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, GoAway obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "debugData":
          if (member.getValue() instanceof String) {
            obj.setDebugData(base64Decode((String)member.getValue()));
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

  private static final java.util.concurrent.atomic.AtomicBoolean base64WarningLogged = new java.util.concurrent.atomic.AtomicBoolean();

  private static io.vertx.core.buffer.Buffer base64Decode(String value) {
    try {
      return io.vertx.core.buffer.Buffer.buffer(java.util.Base64.getUrlDecoder().decode(value));
    } catch (IllegalArgumentException e) {
      io.vertx.core.buffer.Buffer result = io.vertx.core.buffer.Buffer.buffer(java.util.Base64.getDecoder().decode(value));
      if (base64WarningLogged.compareAndSet(false, true)) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        pw.println("Failed to decode a GoAway value with base64url encoding. Used the base64 fallback.");
        e.printStackTrace(pw);
        pw.close();
        System.err.print(sw.toString());
      }
      return result;
    }
  }

   static void toJson(GoAway obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(GoAway obj, java.util.Map<String, Object> json) {
    if (obj.getDebugData() != null) {
      json.put("debugData", java.util.Base64.getUrlEncoder().encodeToString(obj.getDebugData().getBytes()));
    }
    json.put("errorCode", obj.getErrorCode());
    json.put("lastStreamId", obj.getLastStreamId());
  }
}
