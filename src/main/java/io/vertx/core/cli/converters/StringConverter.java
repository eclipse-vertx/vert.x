package io.vertx.core.cli.converters;

/**
 * Converts String to String, that's the easy one.
 */
public final class StringConverter implements Converter<String> {

  /**
   * The converter.
   */
  public static final StringConverter INSTANCE = new StringConverter();

  private StringConverter() {
    // No direct instantiation
  }

  /**
   * Just returns the given input.
   *
   * @param input the input, can be {@literal null}
   * @return the input
   */
  @Override
  public String fromString(String input) throws IllegalArgumentException {
    return input;
  }
}
