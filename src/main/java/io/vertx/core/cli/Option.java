package io.vertx.core.cli;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates a setter to be called with the value of a command line option. Setter have been preferred to field to
 * allow validation.
 * <p/>
 * The cardinality of the option is detected from the single method parameter type: arrays, list and set can receive
 * several values.
 *
 * @see Argument
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Option {

  public static final String NO_SHORT_NAME = "\0";

  /**
   * The name of the option (without the {@code --} prefix).
   * Defaults to a name based on the setter name
   */
  String longName();

  /**
   * The short option name (without the {@code -} prefix).
   * If not given the option has no short name.
   */
  String shortName() default NO_SHORT_NAME;

  /**
   * The name of this argument (used in doc)
   */
  String name() default "value";

  /**
   * Whether or not the option is required.
   */
  boolean required() default false;

  /**
   * Whether or not the option accept a value.
   * If the setter accepts an array, a list, a set, or a collection as parameter, it automatically detect it accepts
   * multiple values.
   */
  boolean acceptValue() default true;
}
