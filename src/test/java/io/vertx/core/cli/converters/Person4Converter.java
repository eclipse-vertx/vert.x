package io.vertx.core.cli.converters;


public class Person4Converter implements Converter<Person4> {
  @Override
  public Person4 fromString(String s) {
    final String[] strings = s.split(",");
    return new Person4(strings[0].trim(), strings[1].trim());
  }
}
