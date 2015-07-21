package io.vertx.core.cli.converters;


public class Person2 {

  public final String name;

  private Person2(String n) {
    this.name = n;
  }

  public static Person2 from(String n) {
    return new Person2(n);
  }
}
