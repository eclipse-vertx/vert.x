package io.vertx.core.cli;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates a {@link Command command} with summary. The summary is the main short explanation of the command. Long
 * description should be written in the {@link Description}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Summary {

  /**
   * The summary. This should be a <strong>single</strong> sentence describing what the command does.
   */
  String value();

}