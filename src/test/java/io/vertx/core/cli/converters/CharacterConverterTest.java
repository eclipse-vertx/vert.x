package io.vertx.core.cli.converters;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;


public class CharacterConverterTest {

  CharacterConverter converter = CharacterConverter.INSTANCE;

  @Test
  public void testFromString() throws Exception {
    assertThat(converter.fromString("a")).isEqualTo('a');
  }

  @Test(expected = NullPointerException.class)
  public void testWithNull() throws Exception {
    converter.fromString(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testWithEmptyString() throws Exception {
    converter.fromString("");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testWithLongString() throws Exception {
    converter.fromString("ab");
  }
}