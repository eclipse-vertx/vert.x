package io.vertx.core.spi.json;

import io.vertx.core.json.jackson.JacksonCodec;

class JacksonCodecLoader {

  static JsonCodec loadJacksonCodec() {
    return new JacksonCodec();
  }
}
