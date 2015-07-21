package io.vertx.core.cli;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to set a default value to an option.
 *
 * @see Option
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DefaultValue {

  /**
   * The (optional) default value of the option. The value is converted to the right type using the
   * {@link io.vertx.core.cli.converters.Converter} set in {@link ConvertedBy}.
   */
  String value();
}
