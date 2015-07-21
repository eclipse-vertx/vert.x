package io.vertx.core.cli.converters;

import java.util.Arrays;
import java.util.List;

/**
 * A converter for boolean. This converter considered as 'true' : "true", "on", "1",
 * "yes". All other values are considered as 'false' (as a consequence, 'null' is considered as 'false').
 */
public final class BooleanConverter implements Converter<Boolean> {

  /**
   * The converter.
   */
  public static final BooleanConverter INSTANCE = new BooleanConverter();
  /**
   * The set of values considered as 'true'.
   */
  private static List<String> TRUE = Arrays.asList("true", "yes", "on", "1");

  private BooleanConverter() {
    // No direct instantiation
  }

  /**
   * Creates the boolean value from the given String. If the given String does not match one of the 'true' value,
   * {@code false} is returned.
   *
   * @param value the value
   * @return the boolean object
   */
  @Override
  public Boolean fromString(String value) {
    return value != null && TRUE.contains(value.toLowerCase());
  }
}
