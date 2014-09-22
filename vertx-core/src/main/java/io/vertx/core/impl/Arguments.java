package io.vertx.core.impl;

/**
 * Helper class to perform extended checks on arguments analogous to
 * {@link java.util.Objects#requireNonNull(Object, String)}.
 */
public class Arguments {

  /**
   * Checks that the specified condition is fulfilled and throws a customized {@link IllegalArgumentException} if it
   * is {@code false}.
   * @param condition condition which must be fulfilled
   * @param message detail message to be used in the event that a {@code
   * IllegalArgumentException} is thrown
   */
  public static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
  }

  /**
   * Checks that the specified number is within the specified minimum and maximum range (inclusively) and throws a
   * customized {@link IllegalArgumentException} if not.
   * @param number value to check
   * @param min minimum allowed value
   * @param max maximum allowed value
   * @param message detail message to be used in the event that a {@code
   * IllegalArgumentException} is thrown
   */
  public static void requireInRange(int number, int min, int max, String message) {
    if (number < min || number > max) {
      throw new IllegalArgumentException(message);
    }
  }

}
