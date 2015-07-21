package io.vertx.core.cli;

import java.util.Collection;

/**
 * Exception thrown when an option was expected and was not found on the command line.
 */
public class MissingOptionException extends CommandLineException {
  private final Collection<OptionModel> expected;

  public MissingOptionException(Collection<OptionModel> expected) {
    super("The option"
        + (expected.size() > 1 ? "s" : "")
        + expected
        + (expected.size() > 1 ? "are" : "is")
        + " required");
    this.expected = expected;
  }

  public Collection<OptionModel> getExpected() {
    return expected;
  }
}
