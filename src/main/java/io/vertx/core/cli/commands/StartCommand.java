package io.vertx.core.cli.commands;

import io.vertx.core.cli.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A command starting a vert.x application in the background.
 */
@Summary("Start a vert.x application in background")
@Description("Start a vert.x application as a background service. The application is identified with an id that can be set using the `vertx.id` option. If not set a random UUID is generated. The application can be stopped with the `stop` command.")
public class StartCommand extends DefaultCommand {

  public static String osName = System.getProperty("os.name").toLowerCase();
  private String id;


  @Option(longName = "vertx.id", shortName = "id", required = false, acceptValue = true)
  @Description("The id of the application, a random UUID by default")
  public void setApplicationId(String id) {
    this.id = id;
  }

  //TODO launcher option

  @Override
  public String name() {
    return "start";
  }

  /**
   * Executes the command.
   *
   * @throws CommandLineException If anything went wrong.
   */
  @Override
  public void run() throws CommandLineException {
    out.println("Starting vert.x application...");
    List<String> cmd = new ArrayList<>();
    cmd.add(getJava().getAbsolutePath());

    if (isLaunchedAsFatJar()) {
      cmd.add("-jar");
      cmd.add(ReflectionUtils.getJar());
    } else {
      cmd.add(ReflectionUtils.getFirstSegmentOfCommand());
    }

    cmd.addAll(getArguments());

    try {
      new ProcessBuilder(cmd).start();
      out.println(id);
    } catch (IOException e) {
      out.println("Cannot create vert.x application process");
      e.printStackTrace(out);
    }

  }

  private File getJava() {
    File java;
    File home = new File(System.getProperty("java.home"));
    if (isWindows()) {
      java = new File(home, "bin/java.exe");
    } else {
      java = new File(home, "bin/java");
    }

    if (!java.isFile()) {
      throw new IllegalStateException("Cannot find java executable - " + java.getAbsolutePath()
          + " does not exist");
    }
    return java;
  }

  private boolean isLaunchedAsFatJar() {
    return ReflectionUtils.getJar() != null;
  }

  private List<String> getArguments() {
    List<String> args = executionContext.getCommandLine().getAllArguments();
    // Add system properties passed as parameter
    args.addAll(
        getSystemProperties().entrySet().stream().map(
            entry -> "-D" + entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.toList()));

    // Add id
    args.add("-Dvertx.id=" + getId());
    return args;
  }

  private String getId() {
    if (id == null) {
      id = UUID.randomUUID().toString();
    }
    return id;
  }


  static public boolean isWindows() {
    return osName.contains("windows");
  }
}
