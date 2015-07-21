package io.vertx.core.cli.converters;


public class Person {

  public final String name;

  /**
   * A constructor that can be used by the engine.
   *
   * @param n the name
   */
  public Person(String n) {
    this.name = n;
  }
}
