package io.vertx.core.http;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import io.vertx.core.spi.json.JsonCodec;

/**
 * Converter and Codec for {@link io.vertx.core.http.StreamPriority}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.http.StreamPriority} original class using Vert.x codegen.
 */
public class StreamPriorityConverter implements JsonCodec<StreamPriority, JsonObject> {

  public static final StreamPriorityConverter INSTANCE = new StreamPriorityConverter();

  @Override public JsonObject encode(StreamPriority value) { return (value != null) ? value.toJson() : null; }

  @Override public StreamPriority decode(JsonObject value) { return (value != null) ? new StreamPriority(value) : null; }

  @Override public Class<StreamPriority> getTargetClass() { return StreamPriority.class; }
}
