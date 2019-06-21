package io.vertx.core.json.codec;

import io.vertx.core.spi.json.JsonCodec;

import java.util.Base64;

public class ByteArrayJsonCodec implements JsonCodec<byte[], String> {

  public static final ByteArrayJsonCodec INSTANCE = new ByteArrayJsonCodec();

  @Override
  public byte[] decode(String json) throws IllegalArgumentException {
    return Base64.getDecoder().decode(json);
  }

  @Override
  public String encode(byte[] value) throws IllegalArgumentException {
    return Base64.getEncoder().encodeToString(value);
  }

  @Override
  public Class<byte[]> getTargetClass() {
    return byte[].class;
  }
}
