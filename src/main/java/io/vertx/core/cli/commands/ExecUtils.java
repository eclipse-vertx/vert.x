package io.vertx.core.cli.commands;

import java.util.List;

/**
 * A couple of utility methods easing process creation.
 */
public class ExecUtils {

  private static final String SINGLE_QUOTE = "\'";
  private static final String DOUBLE_QUOTE = "\"";

  private static String osName = System.getProperty("os.name").toLowerCase();

  /**
   * Put quotes around the given String if necessary.
   * <p>
   * If the argument doesn't include spaces or quotes, return it as is. If it
   * contains double quotes, use single quotes - else surround the argument by
   * double quotes.
   * </p>
   *
   * @param argument the argument to be quoted
   * @return the quoted argument
   * @throws IllegalArgumentException If argument contains both types of quotes
   */
  public static String quoteArgument(final String argument) {

    String cleanedArgument = argument.trim();

    // strip the quotes from both ends
    while (cleanedArgument.startsWith(SINGLE_QUOTE) || cleanedArgument.startsWith(DOUBLE_QUOTE)) {
      cleanedArgument = cleanedArgument.substring(1);
    }

    while (cleanedArgument.endsWith(SINGLE_QUOTE) || cleanedArgument.endsWith(DOUBLE_QUOTE)) {
      cleanedArgument = cleanedArgument.substring(0, cleanedArgument.length() - 1);
    }

    final StringBuilder buf = new StringBuilder();
    if (cleanedArgument.contains(DOUBLE_QUOTE)) {
      if (cleanedArgument.contains(SINGLE_QUOTE)) {
        throw new IllegalArgumentException(
            "Can't handle single and double quotes in same argument");
      }
      return buf.append(SINGLE_QUOTE).append(cleanedArgument).append(
          SINGLE_QUOTE).toString();
    } else if (cleanedArgument.contains(SINGLE_QUOTE)
        || cleanedArgument.contains(" ")) {
      return buf.append(DOUBLE_QUOTE).append(cleanedArgument).append(
          DOUBLE_QUOTE).toString();
    } else {
      return cleanedArgument;
    }
  }

  public static void addArgument(List<String> args, String argument) {
    args.add(quoteArgument(argument));
  }

  public static boolean isWindows() {
    return osName.contains("windows");
  }


}
