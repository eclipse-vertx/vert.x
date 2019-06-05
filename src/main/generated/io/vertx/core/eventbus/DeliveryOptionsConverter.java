package io.vertx.core.eventbus;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import io.vertx.core.spi.json.JsonCodec;

/**
 * Converter and Codec for {@link io.vertx.core.eventbus.DeliveryOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.eventbus.DeliveryOptions} original class using Vert.x codegen.
 */
public class DeliveryOptionsConverter implements JsonCodec<DeliveryOptions, JsonObject> {

  public static final DeliveryOptionsConverter INSTANCE = new DeliveryOptionsConverter();

  @Override public JsonObject encode(DeliveryOptions value) { return (value != null) ? value.toJson() : null; }

  @Override public DeliveryOptions decode(JsonObject value) { return (value != null) ? new DeliveryOptions(value) : null; }

  @Override public Class<DeliveryOptions> getTargetClass() { return DeliveryOptions.class; }
}
