package io.vertx.tests.jacksonv3;

import io.vertx.core.spi.json.JsonCodec;
import io.vertx.tests.json.JsonCodecTest;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;

public class JacksonCodecTest extends JsonCodecTest {

  public JacksonCodecTest() {
    super(new io.vertx.core.json.jackson.v3.JacksonCodec());
  }
}
