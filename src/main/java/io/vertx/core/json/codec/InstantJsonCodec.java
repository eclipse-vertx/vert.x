package io.vertx.core.json.codec;

import io.vertx.core.spi.json.JsonCodec;

import java.time.Instant;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

public class InstantJsonCodec implements JsonCodec<Instant, String> {

  public static final InstantJsonCodec INSTANCE = new InstantJsonCodec();

  @Override
  public Instant decode(String json) throws IllegalArgumentException {
    return Instant.from(ISO_INSTANT.parse(json));
  }

  @Override
  public String encode(Instant value) throws IllegalArgumentException {
    return ISO_INSTANT.format(value);
  }

  @Override
  public Class<Instant> getTargetClass() {
    return Instant.class;
  }
}
