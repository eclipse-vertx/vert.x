package io.vertx.core.tracing;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import io.vertx.core.spi.json.JsonCodec;

/**
 * Converter and Codec for {@link io.vertx.core.tracing.TracingOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.tracing.TracingOptions} original class using Vert.x codegen.
 */
public class TracingOptionsConverter implements JsonCodec<TracingOptions, JsonObject> {

  public static final TracingOptionsConverter INSTANCE = new TracingOptionsConverter();

  @Override public JsonObject encode(TracingOptions value) { return (value != null) ? value.toJson() : null; }

  @Override public TracingOptions decode(JsonObject value) { return (value != null) ? new TracingOptions(value) : null; }

  @Override public Class<TracingOptions> getTargetClass() { return TracingOptions.class; }

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, TracingOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "enabled":
          if (member.getValue() instanceof Boolean) {
            obj.setEnabled((Boolean)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(TracingOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(TracingOptions obj, java.util.Map<String, Object> json) {
    json.put("enabled", obj.isEnabled());
  }
}
