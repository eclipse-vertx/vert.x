package io.vertx.core.jacksonv3;

import io.vertx.core.spi.JsonFactory;
import io.vertx.core.spi.json.JsonCodec;

public class JacksonFactory implements JsonFactory {

  @Override
  public JsonCodec codec() {
    return new JacksonCodec();
  }
}
