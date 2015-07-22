package io.vertx.core.cli;

public class InvalidValueException extends CommandLineException {
  private final OptionModel option;
  private final ArgumentModel argument;
  private final String value;

  public InvalidValueException(OptionModel option, String value) {
    this(option, value, null);
  }

  public InvalidValueException(ArgumentModel argument, String value) {
    this(argument, value, null);
  }

  public InvalidValueException(ArgumentModel argument, String value, Exception cause) {
    super("The value '" + value + "' is not accepted by the argument '"
        + (argument.getArgName() != null ? argument.getArgName():argument.getIndex()) + "'", cause);
    this.option = null;
    this.value = value;
    this.argument = argument;
  }

  public <T> InvalidValueException(OptionModel option, String value, Exception cause) {
    super("The value '" + value + "' is not accepted by " + option, cause);
    this.argument = null;
    this.value = value;
    this.option = option;
  }

  public OptionModel getOption() {
    return option;
  }

  public String getValue() {
    return value;
  }

  public ArgumentModel getArgument() {
    return argument;
  }
}
