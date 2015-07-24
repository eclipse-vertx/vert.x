package io.vertx.core.cli.commands;

import io.vertx.core.cli.*;

@Summary("A command saying hello.")
@Description("A simple command to wish you a good day. Pass your name with `--name`")
public class HelloCommand extends DefaultCommand {

  public String name;


  @Option(longName = "name", shortName = "n", required = true, name = "name")
  @Description("your name")
  public void setName(String name) {
    this.name = name;
  }

  /**
   * @return the command name such as 'run'.
   */
  @Override
  public String name() {
    return "hello";
  }
  /**
   * Executes the command.
   *
   * @throws CommandLineException If anything went wrong.
   */
  @Override
  public void run() throws CommandLineException {
    out.println("Hello " + name);
  }
}
