package io.vertx.core.spi.cli;

import java.util.Collection;
import java.util.HashMap;

import io.vertx.core.ServiceHelper;

public class CliJsonProcessors {

  private static final HashMap<String, CliJsonProcessor> CLI_JSON_PROCESSORS = new HashMap<>();

  public CliJsonProcessors() {
  }

  static {
    synchronized (CliJsonProcessors.class) {
      Collection<CliJsonProcessor> cliJsonProcessors = ServiceHelper.loadFactories(CliJsonProcessor.class);
      cliJsonProcessors.forEach(c -> CLI_JSON_PROCESSORS.put(c.name(), c));
    }
  }

  public static CliJsonProcessor get(String format) {
    synchronized (CliJsonProcessors.class) {
      return CLI_JSON_PROCESSORS.get(format);
    }
  }


}
