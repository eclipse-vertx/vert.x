package io.vertx.core.cli.commands;

import io.vertx.core.cli.*;

@Hidden
public class HiddenCommand extends DefaultCommand {

  public String name;


  @Option(longName = "name", shortName = "n", required = true)
  public void setName(String name) {
    this.name = name;
  }

  /**
   * @return the command name such as 'run'.
   */
  @Override
  public String name() {
    return "hidden";
  }

  /**
   * Executes the command.
   *
   * @throws CommandLineException If anything went wrong.
   */
  @Override
  public void run() throws CommandLineException {
    System.out.println("Do something hidden...");
  }
}
