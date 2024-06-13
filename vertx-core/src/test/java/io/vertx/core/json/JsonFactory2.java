package io.vertx.core.json;

import io.vertx.core.buffer.Buffer;
import io.vertx.impl.core.json.jackson.JacksonCodec;
import io.vertx.core.parsetools.JsonParser;
import io.vertx.core.spi.JsonFactory;
import io.vertx.core.spi.json.JsonCodec;
import io.vertx.core.streams.ReadStream;

import java.util.Random;

public class JsonFactory2 implements JsonFactory  {

  static JsonCodec CODEC = new JacksonCodec();

  public static final int ORDER = new Random().nextInt();

  @Override
  public int order() {
    return ORDER;
  }

  @Override
  public JsonCodec codec() {
    return CODEC;
  }

  @Override
  public JsonParser parser(ReadStream<Buffer> stream) {
    throw new UnsupportedOperationException();
  }
}
