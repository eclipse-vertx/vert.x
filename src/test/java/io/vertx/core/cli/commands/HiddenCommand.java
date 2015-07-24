package io.vertx.core.cli.commands;

import io.vertx.core.cli.CommandLineException;
import io.vertx.core.cli.DefaultCommand;
import io.vertx.core.cli.Hidden;
import io.vertx.core.cli.Option;

@Hidden
public class HiddenCommand extends DefaultCommand {

  public String name;
  private int count;


  @Option(longName = "name", shortName = "n", required = true)
  public void setName(String name) {
    this.name = name;
  }

  @Option(longName = "count", shortName = "c")
  @Hidden
  public void setCount(int count) {
    this.count = count;
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
    out.println("Do something hidden...");
  }
}
