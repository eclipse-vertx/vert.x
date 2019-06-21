package io.vertx.core.json.codec;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.spi.json.JsonCodec;

public class BufferJsonCodec implements JsonCodec<Buffer, String> {

  public static final BufferJsonCodec INSTANCE = new BufferJsonCodec();

  @Override
  public Buffer decode(String json) throws IllegalArgumentException {
    return Buffer.buffer(ByteArrayJsonCodec.INSTANCE.decode(json));
  }

  @Override
  public String encode(Buffer value) throws IllegalArgumentException {
    return ByteArrayJsonCodec.INSTANCE.encode(value.getBytes());
  }

  @Override
  public Class<Buffer> getTargetClass() {
    return Buffer.class;
  }
}
