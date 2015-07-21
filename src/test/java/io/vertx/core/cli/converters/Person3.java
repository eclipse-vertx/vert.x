package io.vertx.core.cli.converters;


public class Person3 {

  public final String name;

  private Person3(String n) {
    this.name = n;
  }

  public static Person3 fromString(String n) {
    return new Person3(n);
  }
}
