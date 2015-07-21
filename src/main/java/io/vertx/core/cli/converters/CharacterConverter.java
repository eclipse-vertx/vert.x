package io.vertx.core.cli.converters;


/**
 * A converter for character. Unlike other primitive types, characters cannot be created using 'valueOf'. Notice that
 * only input having a length of 1 can be converted to characters. Other inputs are rejected.
 */
public final class CharacterConverter implements Converter<Character> {

  /**
   * The converter.
   */
  public static final CharacterConverter INSTANCE = new CharacterConverter();

  private CharacterConverter() {
    // No direct instantiation
  }

  @Override
  public Character fromString(String input) throws IllegalArgumentException {
    if (input == null) {
      throw new NullPointerException("input must not be null");
    }

    if (input.length() != 1) {
      throw new IllegalArgumentException("The input string \"" + input + "\" cannot be converted to a " +
          "character. The input's length must be 1");
    }

    return input.toCharArray()[0];

  }
}
