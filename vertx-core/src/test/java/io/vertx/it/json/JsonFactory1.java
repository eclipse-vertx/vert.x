package io.vertx.it.json;

import io.vertx.core.json.jackson.JacksonCodec;
import io.vertx.core.spi.JsonFactory;
import io.vertx.core.spi.json.JsonCodec;

import java.util.Random;

public class JsonFactory1 implements JsonFactory  {

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
}
