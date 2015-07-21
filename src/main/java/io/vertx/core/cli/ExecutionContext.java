package io.vertx.core.cli;

/**
 * The execution context contains various information on the execution. It's mainly the built command line.
 */
public class ExecutionContext {
  private CommandLine commandLine;

  ExecutionContext(CommandLine cl) {
    commandLine = cl;
  }

  public CommandLine getCommandLine() {
    return commandLine;
  }
}
