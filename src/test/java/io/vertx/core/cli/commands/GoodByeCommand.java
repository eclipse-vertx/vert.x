package io.vertx.core.cli.commands;

import io.vertx.core.cli.*;

@Summary("A command saying goodbye.")
public class GoodByeCommand extends DefaultCommand {

  private ExecutionContext context;

  /**
   * @return the command name such as 'run'.
   */
  @Override
  public String name() {
    return "bye";
  }

  /**
   * Initializes the command.
   * Use this for setup, special handling of options and argument, validation
   *
   * @param ec the execution context
   * @throws CommandLineException If anything went wrong.
   */
  @Override
  public void initialize(ExecutionContext ec) throws CommandLineException {
    this.context = ec;
    this.context.getCommandLine().addOption(OptionModel.<String>builder()
        .isRequired()
        .shortName("n")
        .acceptValue()
        .type(String.class)
        .build());
  }

  /**
   * Executes the command.
   *
   * @throws CommandLineException If anything went wrong.
   */
  @Override
  public void run() throws CommandLineException {
    final String n = this.context.getCommandLine().getOptionValue("n");
    System.out.println("Good Bye " + n);
  }
}
