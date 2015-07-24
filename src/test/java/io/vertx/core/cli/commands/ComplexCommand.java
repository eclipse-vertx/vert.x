package io.vertx.core.cli.commands;

import io.vertx.core.cli.*;

@Summary("A command with options and arguments.")
@Description("This is a complex command.")
public class ComplexCommand extends DefaultCommand {

  private String arg1;
  private int arg2;
  private String option1;
  private boolean option2;

  @Argument(index = 0, name = "arg1")
  public void setArgument1(String arg1) {
    this.arg1 = arg1;
  }

  @Argument(index = 1, name = "arg2", required = false)
  public void setArgument2(int arg2) {
    this.arg2 = arg2;
  }

  @Option(
      longName = "option1",
      shortName = "o1",
      name = "opt",
      required = true
  )
  public void setOption1(String option1) {
    this.option1 = option1;
  }

  @Option(
      longName = "option2",
      shortName = "o2",
      acceptValue = false,
      required = false
  )
  public void setOption2(boolean option2) {
    this.option2 = option2;
  }

  @Override
  public String name() {
    return "complex";
  }


  @Override
  public void run() throws CommandLineException {
    out.println("Option 1 : " + option1);
    out.println("Option 2 : " + option2);
    out.println("Arg 1 : " + arg1);
    out.println("Arg 2 : " + arg2);
    if (executionContext.getCommandLine().getAllArguments().size() > 2) {
      out.println("All args: " + executionContext.getCommandLine().getAllArguments());
    }
  }
}
