package io.vertx.tests.jacksonv3;

import io.vertx.tests.json.JsonCodecTest;


public class JacksonCodecTest extends JsonCodecTest {

  public JacksonCodecTest() {
    super(new io.vertx.core.json.jackson.v3.JacksonCodec());
  }
}
