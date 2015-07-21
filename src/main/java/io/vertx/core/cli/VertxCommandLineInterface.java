package io.vertx.core.cli;

import java.util.*;

/**
 * The entry point of the Vert.x Command Line interface.
 */
public class VertxCommandLineInterface extends UsageMessageFormatter {

  protected final List<CommandLookup> lookups;

  protected final Map<String, Command> commandByName;

  public VertxCommandLineInterface() {
    this(Collections.singletonList(new ServiceCommandLoader()));
  }

  public VertxCommandLineInterface(Collection<CommandLookup> lookups) {
    this.lookups = new ArrayList<>(lookups);
    this.commandByName = new TreeMap<>();
    load();
  }

  protected void load() {
    for (CommandLookup lookup : lookups) {
      final Collection<Command> commands = lookup.lookup();
      for (Command command : commands) {
        commandByName.put(command.name(), command);
      }
    }
  }

  public Collection<String> getCommandNames() {
    return commandByName.keySet();
  }

  public Collection<Command> getCommands() {
    return commandByName.values();
  }

  public void execute(String command, String... cla) {
    if (command == null || isAskingForHelp(command)) {
      printGlobalUsage();
      return;
    }

    Command cmd = commandByName.get(command);
    if (cmd == null) {
      printCommandNotFound(command);
      return;
    }

    CommandLine line = new CommandLine();
    ExecutionContext context = new ExecutionContext(line);
    try {
      // Step 1 - definition
      CommandManager.define(cmd, line);
      cmd.initialize(context);

      // Check for help - the command need to have been initialized ot get the complete model.
      if (cla.length >= 1 && isAskingForHelp(cla[0])) {
        printCommandUsage(cmd, line);
        return;
      }

      // Step 2 - parsing and injection
      line.parse(cla);
      CommandManager.inject(cmd, line);

      // Step 3 - validation
      cmd.setup();

      // Step 4 - execution
      cmd.run();

      // Step 5 - cleanup
      cmd.tearDown();
    } catch (MissingOptionException | MissingValueException e) {
      printSpecificException(cmd, line, e);
    } catch (CommandLineException e) {
      // Generic error
      printGenericExecutionError(cmd, line, e);
    }
  }

  private void printCommandUsage(Command command, CommandLine line) {
    StringBuilder builder = new StringBuilder();

    String header = getNewLine()
        + CommandManager.getSummary(command) + getNewLine()
        + CommandManager.getDescription(command) + getNewLine()
        + getNewLine();

    computeCommandUsage(builder, getCommandLinePrefix() + " " + command.name(), header, line, "", true);
    System.out.println(builder.toString());
  }

  protected void printGenericExecutionError(Command cmd, CommandLine line, CommandLineException e) {
    System.out.println("Error while executing command " + cmd.name() + ": " + e.getMessage() + getNewLine());
    if (e.getCause() != null) {
      e.getCause().printStackTrace(System.out);
    }
  }

  protected void printSpecificException(Command cmd, CommandLine line, Exception e) {
    System.out.println(e.getMessage() + getNewLine());
    printCommandUsage(cmd, line);
  }

  protected void printCommandNotFound(String command) {
    StringBuilder builder = new StringBuilder();
    buildWrapped(builder, 0, "The command '" + command + "' is not a valid command." + getNewLine()
        + "See '" + getCommandLinePrefix() + " --help'");
    System.out.println(builder.toString());
  }

  protected void printGlobalUsage() {
    StringBuilder builder = new StringBuilder();

    computeUsage(builder, getCommandLinePrefix() + " [COMMAND] [OPTIONS] [arg...]");

    builder.append(getNewLine());
    builder.append("Commands:").append(getNewLine());

    renderCommands(builder, commandByName.values());

    builder.append(getNewLine()).append(getNewLine());

    buildWrapped(builder, 0, "Run '" + getCommandLinePrefix() + " COMMAND --help' for more information on a command.");

    System.out.println(builder.toString());
  }

  protected String getCommandLinePrefix() {
    // Let's try to do an educated guess.

    // Check whether or not the "sun.java.command" system property is defined
    final String property = System.getProperty("sun.java.command");
    if (property != null) {
      final String[] segments = property.split(" ");
      if (segments.length >= 1) {
        // Fat Jar ?
        if (segments[0].endsWith(".jar")) {
          return "java -jar " + segments[0];
        } else {
          // Starter or another launcher passed as command line
          return "java " + segments[0];
        }
      }
    }
    return "vertx";
  }

  public static boolean isAskingForHelp(String command) {
    return command.equalsIgnoreCase("--help")
        || command.equalsIgnoreCase("-help")
        || command.equalsIgnoreCase("-h")
        || command.equalsIgnoreCase("?")
        || command.equalsIgnoreCase("/?");
  }

}
