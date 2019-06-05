package io.vertx.core.net;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import io.vertx.core.spi.json.JsonCodec;

/**
 * Converter and Codec for {@link io.vertx.core.net.JdkSSLEngineOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.net.JdkSSLEngineOptions} original class using Vert.x codegen.
 */
public class JdkSSLEngineOptionsConverter implements JsonCodec<JdkSSLEngineOptions, JsonObject> {

  public static final JdkSSLEngineOptionsConverter INSTANCE = new JdkSSLEngineOptionsConverter();

  @Override public JsonObject encode(JdkSSLEngineOptions value) { return (value != null) ? value.toJson() : null; }

  @Override public JdkSSLEngineOptions decode(JsonObject value) { return (value != null) ? new JdkSSLEngineOptions(value) : null; }

  @Override public Class<JdkSSLEngineOptions> getTargetClass() { return JdkSSLEngineOptions.class; }
}
