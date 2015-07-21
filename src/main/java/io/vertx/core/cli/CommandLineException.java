package io.vertx.core.cli;

/**
 * High level exception thrown when an issue in the command line processing occurs.
 */
public class CommandLineException extends Exception {
  public CommandLineException(String message) {
    super(message);
  }

  public CommandLineException(String message, Exception cause) {
    super(message, cause);
  }
}
