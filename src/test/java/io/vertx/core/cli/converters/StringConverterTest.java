package io.vertx.core.cli.converters;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;


public class StringConverterTest {

  @Test
  public void testFromString() throws Exception {
    assertThat(StringConverter.INSTANCE.fromString("hello")).isEqualTo("hello");
    assertThat(StringConverter.INSTANCE.fromString("")).isEqualTo("");
    assertThat(StringConverter.INSTANCE.fromString(null)).isEqualTo(null);
  }
}