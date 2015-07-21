package io.vertx.core.cli;

import io.vertx.core.cli.converters.Converter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Annotates {@link Option @Option} setters to indicate how the value is converted to the argument type.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ConvertedBy {

  /**
   * The converter class used to transform the value as String to the target type. This converter is also used for
   * the {@link Option#defaultValue()}.
   */
  Class<? extends Converter<?>> value();

}
