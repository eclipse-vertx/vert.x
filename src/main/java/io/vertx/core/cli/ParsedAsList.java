package io.vertx.core.cli;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates a setter to be called with the value of a command line option. The setter must also have been annotated
 * with {@link Option}.
 * <p/>
 * When annotated with {@link ParsedAsList}, the option value is parsed as a list. The value is split and then each
 * segment is trimmed.
 *
 * @see Option
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ParsedAsList {

  /**
   * The separator used to split the value. {@code ,} is used by default.
   */
  String separator() default ",";
}
