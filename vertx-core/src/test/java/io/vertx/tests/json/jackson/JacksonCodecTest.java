package io.vertx.tests.json.jackson;

import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.core.json.jackson.JacksonCodec;
import io.vertx.core.spi.json.JsonCodec;
import io.vertx.tests.json.JsonCodecTest;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public class JacksonCodecTest extends JsonCodecTest {

  @Parameterized.Parameters
  public static Collection<Object[]> mappers() {
    return Arrays.asList(new Object[][] {
      { new DatabindCodec() }, { new JacksonCodec() }
    });
  }

  public JacksonCodecTest(JsonCodec codec) {
    super(codec);
  }
}
