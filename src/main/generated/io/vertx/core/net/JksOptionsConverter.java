package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter for {@link io.vertx.core.net.JksOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.JksOptions} original class using Vert.x codegen.
 */
public class JksOptionsConverter {

  public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, JksOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "password":
          if (member.getValue() instanceof String) {
            obj.setPassword((String)member.getValue());
          }
          break;
        case "path":
          if (member.getValue() instanceof String) {
            obj.setPath((String)member.getValue());
          }
          break;
        case "value":
          if (member.getValue() instanceof String) {
            obj.setValue(base64Decode((String)member.getValue()));
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
        pw.println("Failed to decode a JksOptions value with base64url encoding. Used the base64 fallback.");
        e.printStackTrace(pw);
        pw.close();
        System.err.print(sw.toString());
      }
      return result;
    }
  }

  public static void toJson(JksOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

  public static void toJson(JksOptions obj, java.util.Map<String, Object> json) {
    if (obj.getPassword() != null) {
      json.put("password", obj.getPassword());
    }
    if (obj.getPath() != null) {
      json.put("path", obj.getPath());
    }
    if (obj.getValue() != null) {
      json.put("value", java.util.Base64.getUrlEncoder().encodeToString(obj.getValue().getBytes()));
    }
  }
}
