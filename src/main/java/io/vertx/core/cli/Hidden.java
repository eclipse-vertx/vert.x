package io.vertx.core.cli;

import io.vertx.core.spi.Command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Annotates a {@link Command command} and/or its {@link Option @Option} setters to hide it from the help message.
 *
 * @see Summary
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Hidden {
  // Just a marker.
}
