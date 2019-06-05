package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import io.vertx.core.spi.json.JsonCodec;

/**
 * Converter and Codec for {@link io.vertx.core.net.PfxOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.PfxOptions} original class using Vert.x codegen.
 */
public class PfxOptionsConverter implements JsonCodec<PfxOptions, JsonObject> {

  public static final PfxOptionsConverter INSTANCE = new PfxOptionsConverter();

  @Override public JsonObject encode(PfxOptions value) { return (value != null) ? value.toJson() : null; }

  @Override public PfxOptions decode(JsonObject value) { return (value != null) ? new PfxOptions(value) : null; }

  @Override public Class<PfxOptions> getTargetClass() { return PfxOptions.class; }

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, PfxOptions obj) {
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
            obj.setValue(io.vertx.core.buffer.Buffer.buffer(java.util.Base64.getDecoder().decode((String)member.getValue())));
          }
          break;
      }
    }
  }

   static void toJson(PfxOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(PfxOptions obj, java.util.Map<String, Object> json) {
    if (obj.getPassword() != null) {
      json.put("password", obj.getPassword());
    }
    if (obj.getPath() != null) {
      json.put("path", obj.getPath());
    }
    if (obj.getValue() != null) {
      json.put("value", java.util.Base64.getEncoder().encodeToString(obj.getValue().getBytes()));
    }
  }
}
