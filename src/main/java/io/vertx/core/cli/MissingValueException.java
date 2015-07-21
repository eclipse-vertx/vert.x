package io.vertx.core.cli;

public class MissingValueException extends CommandLineException {
  private final OptionModel option;
  private final ArgumentModel argument;

  public MissingValueException(OptionModel option) {
    super("The option " + option.toString() + " requires a value");
    this.argument = null;
    this.option = option;
  }

  public MissingValueException(ArgumentModel argument) {
    super("The argument '"
        + (argument.getArgName() != null ? argument.getArgName():argument.getIndex())
        + "' is required");
    this.option = null;
    this.argument = argument;
  }

  public OptionModel getOption() {
    return option;
  }

  public ArgumentModel getArgument() {
    return argument;
  }
}
