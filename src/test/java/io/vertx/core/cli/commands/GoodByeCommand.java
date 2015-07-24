package io.vertx.core.cli.commands;

import io.vertx.core.cli.*;

import java.util.List;

@Summary("A command saying goodbye.")
public class GoodByeCommand extends DefaultCommand {

  /**
   * @return the command name such as 'run'.
   */
  @Override
  public String name() {
    return "bye";
  }

  @Override
  public List<OptionModel> options() {
    List<OptionModel> list = super.options();
    list.add(OptionModel.<String>builder()
        .isRequired()
        .shortName("n")
        .acceptValue()
        .type(String.class)
        .build());
    return list;
  }

  /**
   * Executes the command.
   *
   * @throws CommandLineException If anything went wrong.
   */
  @Override
  public void run() throws CommandLineException {
    final String n = executionContext.getCommandLine().getOptionValue("n");
    out.println("Good Bye " + n);
  }
}
