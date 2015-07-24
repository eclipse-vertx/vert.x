package io.vertx.core.cli;

import io.vertx.core.spi.Command;

import java.io.PrintStream;
import java.util.HashMap;

/**
 * The execution context contains various information on the execution. It's mainly the built command line.
 */
public class ExecutionContext extends HashMap<String, Object> {
  private final VertxCommandLineInterface cli;
  private final CommandLine commandLine;

  ExecutionContext(CommandLine commandLine, VertxCommandLineInterface cli) {
    this.commandLine = commandLine;
    this.cli = cli;
  }

  /**
   * @return the command line object.
   */
  public CommandLine getCommandLine() {
    return commandLine;
  }

  /**
   * Reports an error.
   *
   * @param command   the erroneous command
   * @param exception the exception
   */
  public void reportError(Command command, Exception exception) {
    cli.printSpecificException(command, commandLine, exception);
  }

  /**
   * Executes another command.
   *
   * @param command the command name
   * @param args    the arguments
   */
  public void execute(String command, String... args) {
    cli.execute(command, args);
  }

  public Object main() {
    return get("Main");
  }

  /**
   * @return the {@link PrintStream} on which command can write.
   */
  public PrintStream getPrintStream() {
    return cli.getPrintStream();
  }
}
