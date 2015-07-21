package io.vertx.core.cli;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates a setter to be called with the value of a command line argument.
 *
 * @see Option
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Argument {

  /**
   * The name of this argument (used in doc)
   */
  String name() default "value";

  /**
   * The (0-based) position of this argument relative to the argument list. The first parameter has the index 0,
   * the second 1...
   * <p/>
   * Index is mandatory to force you to think to the order.
   */
  int index();

  /**
   * Whether or not the argument is required. An argument is required by default.
   */
  boolean required() default true;
}
