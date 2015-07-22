package io.vertx.core.cli;

import java.util.List;

/**
 * Exception thrown when the command line is ambiguous meaning it cannot determine exactly which option has to be set.
 */
public class AmbiguousOptionException extends CommandLineException {

  private final List<OptionModel> options;
  private final String token;


  public AmbiguousOptionException(String token, List<OptionModel> matchingOpts) {
    super("Ambiguous argument in command line: '" + token + "' matches " + matchingOpts);
    this.token = token;
    this.options = matchingOpts;
  }

  public List<OptionModel> getOptions() {
    return options;
  }

  public String getToken() {
    return token;
  }
}
