package io.vertx.core.cli;

import io.vertx.core.spi.Command;

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

  public CommandLine getCommandLine() {
    return commandLine;
  }

  public void reportError(Command command, Exception exception) {
    cli.printSpecificException(command, commandLine, exception);
  }

  public void execute(String command, String... args) {
    cli.execute(command, args);
  }

  public Object main() {
    return get("Main");
  }
}
