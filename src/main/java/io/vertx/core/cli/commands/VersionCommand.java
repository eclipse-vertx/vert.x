package io.vertx.core.cli.commands;

import io.vertx.core.cli.CommandLineException;
import io.vertx.core.cli.DefaultCommand;
import io.vertx.core.cli.Summary;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

/**
 * Display the vert.x version.
 */
@Summary("Displays the version.")
public class VersionCommand extends DefaultCommand {

  private static final Logger log = LoggerFactory.getLogger(VersionCommand.class);


  @Override
  public String name() {
    return "version";
  }

  @Override
  public void run() throws CommandLineException {
    log.info(getVersion());
  }

  public String getVersion() {
    try (InputStream is = getClass().getClassLoader().getResourceAsStream("vertx-version.txt")) {
      if (is == null) {
        throw new IllegalStateException("Cannot find vertx-version.txt on classpath");
      }
      try (Scanner scanner = new Scanner(is, "UTF-8").useDelimiter("\\A")) {
        return scanner.hasNext() ? scanner.next() : "";
      }
    } catch (IOException e) {
      throw new IllegalStateException(e.getMessage());
    }
  }
}
