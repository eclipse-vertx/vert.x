package io.vertx.core.cli.converters;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;


public class CustomConverterTest {

  @Test
  public void testCreation() {
    assertThat(new Person4Converter().fromString("bob, morane").first).isEqualToIgnoringCase("bob");
    assertThat(new Person4Converter().fromString("bob, morane").last).isEqualToIgnoringCase("morane");
  }

  @Test
  public void testConvertion() {
    Person4 p4 = Converters.create("bob, morane", new Person4Converter());
    assertThat(p4.first).isEqualTo("bob");
    assertThat(p4.last).isEqualTo("morane");
  }

}