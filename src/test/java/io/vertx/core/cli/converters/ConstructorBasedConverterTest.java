package io.vertx.core.cli.converters;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;


public class ConstructorBasedConverterTest {


  @Test
  public void testGetIfEligible() throws Exception {
    assertThat(ConstructorBasedConverter.getIfEligible(Person.class)).isNotNull();
    assertThat(ConstructorBasedConverter.getIfEligible(Object.class)).isNull();
  }

  @Test
  public void testFromString() throws Exception {
    assertThat(ConstructorBasedConverter.getIfEligible(Person.class).fromString("vertx").name).isEqualTo("vertx");
  }
}