/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl.launcher;

import io.vertx.core.cli.*;
import io.vertx.core.cli.annotations.CLIConfigurator;
import io.vertx.core.impl.launcher.commands.RunCommand;
import io.vertx.core.spi.launcher.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

/**
 * The entry point of the Vert.x Command Line interface.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class VertxCommandLauncher extends UsageMessageFormatter {

  protected static List<String> PROCESS_ARGS;

  /**
   * @return the process argument. Verticles can use this method to retrieve the arguments.
   */
  public static List<String> getProcessArguments() {
    return PROCESS_ARGS;
  }

  /**
   * the list of lookups.
   */
  protected final List<CommandFactoryLookup> lookups;

  /**
   * the list of commands. Sub-classes can decide to remove commands by removing entries from this map.
   */
  protected final Map<String, CommandRegistration> commandByName;

  /**
   * the {@code Main-Class} object.
   */
  protected Object main;

  /**
   * Handles a command registration.
   */
  public static class CommandRegistration {
    public final CommandFactory factory;
    public final CLI cli;
    private List<Command> commands = new ArrayList<>();

    public CommandRegistration(CommandFactory factory) {
      this(factory, factory.define());
    }

    public CommandRegistration(CommandFactory factory, CLI cli) {
      this.factory = factory;
      this.cli = cli;
    }

    public void addCommand(Command command) {
      commands.add(command);
    }

    public Command getCommand() {
      if (!commands.isEmpty()) {
        return commands.get(0);
      }
      return null;
    }

    public List<Command> getCommands() {
      return commands;
    }
  }

  /**
   * Creates a new {@link VertxCommandLauncher} using the default {@link ServiceCommandFactoryLoader}. It uses the
   * classloader having loaded {@link ServiceCommandFactoryLoader}.
   */
  public VertxCommandLauncher() {
    this(Collections.singletonList(new ServiceCommandFactoryLoader()));
  }

  /**
   * Creates a new {@link VertxCommandLauncher} using the given list of {@link CommandFactoryLookup}.
   *
   * @param lookups the list of lookup
   */
  public VertxCommandLauncher(Collection<CommandFactoryLookup> lookups) {
    this.lookups = new ArrayList<>(lookups);
    this.commandByName = new TreeMap<>();
    load();
  }

  /**
   * Loads the command. This method is {@link protected} to let sub-classes change the set of command or how
   * they are loaded.
   */
  protected void load() {
    for (CommandFactoryLookup lookup : lookups) {
      Collection<CommandFactory<?>> commands = lookup.lookup();
      commands.forEach(this::register);
    }
  }

  public VertxCommandLauncher register(CommandFactory factory) {
    CLI cli = factory.define();
    commandByName.put(cli.getName(), new CommandRegistration(factory, cli));
    return this;
  }

  @SuppressWarnings("unchecked")
  public VertxCommandLauncher register(Class<? extends Command> clazz) {
    DefaultCommandFactory factory = new DefaultCommandFactory(clazz);
    CLI cli = factory.define();
    commandByName.put(cli.getName(), new CommandRegistration(factory, cli));
    return this;
  }

  public VertxCommandLauncher unregister(String name) {
    commandByName.remove(name);
    return this;
  }

  /**
   * @return the list of command.
   */
  public Collection<String> getCommandNames() {
    return commandByName.keySet();
  }

  /**
   * Creates a new {@link Command} instance. Sub-classes can change how {@link Command} instance are created.
   *
   * @param name        the command name
   * @param commandLine the command line
   * @return the new instance, {@code null} if the command cannot be found.
   */
  protected Command getNewCommandInstance(String name, CommandLine commandLine) {
    CommandRegistration registration = commandByName.get(name);
    if (registration != null) {
      Command command = registration.factory.create(commandLine);
      registration.addCommand(command);
      return command;
    }
    return null;
  }

  /**
   * Gets an existing instance of command.
   *
   * @param name the command name
   * @return the {@link Command} instance, {@code null} if not found
   */
  public Command getExistingCommandInstance(String name) {
    CommandRegistration registration = commandByName.get(name);
    if (registration != null) {
      return registration.getCommand();
    }
    return null;
  }

  /**
   * Executes the given command.
   *
   * @param command the command name
   * @param cla     the arguments
   */
  public void execute(String command, String... cla) {
    if (command != null && isAskingForVersion(command)) {
      execute("version");
      return;
    }

    if (command == null || isAskingForHelp(command)) {
      printGlobalUsage();
      return;
    }

    CommandRegistration registration = commandByName.get(command);
    if (registration == null) {
      printCommandNotFound(command);
      return;
    }

    CLI cli = registration.cli;

    try {
      // Check for help - the command need to have been initialized ot get the complete model.
      if (cla.length >= 1 && isAskingForHelp(cla[0])) {
        printCommandUsage(cli);
        return;
      }

      // Step 1 - parsing and injection
      CommandLine evaluated = cli.parse(Arrays.asList(cla));
      Command cmd = getNewCommandInstance(command, evaluated);
      ExecutionContext context = new ExecutionContext(cmd, this, evaluated);
      if (main != null) {
        context.put("Main", main);
        context.put("Main-Class", main.getClass().getName());
      }

      CLIConfigurator.inject(evaluated, cmd);

      // Step 2 - validation
      cmd.setUp(context);

      // Step 3 - execution
      cmd.run();

      // Step 4 - cleanup
      cmd.tearDown();
    } catch (MissingOptionException | MissingValueException | InvalidValueException e) {
      printSpecificException(cli, e);
    } catch (CLIException e) {
      printGenericExecutionError(cli, e);
    } catch (RuntimeException e) {
      if (e.getCause() instanceof CLIException) {
        printGenericExecutionError(cli, (CLIException) e.getCause());
        return;
      }
      throw e;
    }
  }


  protected void printCommandUsage(CLI cli) {
    StringBuilder builder = new StringBuilder();
    cli.usage(builder, getCommandLinePrefix());
    getPrintStream().println(builder.toString());
  }

  protected void printGenericExecutionError(CLI cli, CLIException e) {
    getPrintStream().println("Error while executing command " + cli.getName() + ": " + e.getMessage() + getNewLine());
    if (e.getCause() != null) {
      e.getCause().printStackTrace(getPrintStream());
    }
  }

  protected void printSpecificException(CLI cli, Exception e) {
    getPrintStream().println(e.getMessage() + getNewLine());
    printCommandUsage(cli);
  }

  protected void printCommandNotFound(String command) {
    StringBuilder builder = new StringBuilder();
    buildWrapped(builder, 0, "The command '" + command + "' is not a valid command." + getNewLine()
        + "See '" + getCommandLinePrefix() + " --help'");
    getPrintStream().println(builder.toString());
  }

  protected void printGlobalUsage() {
    StringBuilder builder = new StringBuilder();

    computeUsage(builder, getCommandLinePrefix() + " [COMMAND] [OPTIONS] [arg...]");

    builder.append(getNewLine());
    builder.append("Commands:").append(getNewLine());

    renderCommands(builder, commandByName.values().stream().map(r -> r.cli).collect(Collectors.toList()));

    builder.append(getNewLine()).append(getNewLine());

    buildWrapped(builder, 0, "Run '" + getCommandLinePrefix() + " COMMAND --help' for more information on a command.");

    getPrintStream().println(builder.toString());
  }

  protected String getCommandLinePrefix() {
    // Check whether `vertx.cli.usage.prefix` is set, if so use it. This system property let scripts configure the value
    // displayed by the usage, even if they are calling java.
    String sysProp = System.getProperty("vertx.cli.usage.prefix");
    if (sysProp != null) {
      return sysProp;
    }

    String jar = CommandLineUtils.getJar();
    if (jar != null) {
      return "java -jar " + jar;
    }
    String command = CommandLineUtils.getFirstSegmentOfCommand();
    if (command != null) {
      return "java " + command;
    }

    return "vertx";
  }

  protected static boolean isAskingForHelp(String command) {
    return command.equalsIgnoreCase("--help")
        || command.equalsIgnoreCase("-help")
        || command.equalsIgnoreCase("-h")
        || command.equalsIgnoreCase("?")
        || command.equalsIgnoreCase("/?");
  }

  protected static boolean isAskingForVersion(String command) {
    return command.equalsIgnoreCase("-version") || command.equalsIgnoreCase("--version");
  }

  /**
   * Dispatches to the right command. This method is generally called from the {@code main} method.
   *
   * @param args the command line arguments.
   */
  public void dispatch(String[] args) {
    dispatch(null, args);
  }

  /**
   * Dispatches to the right command. This method is generally called from the {@code main} method.
   *
   * @param main the main instance on which hooks and callbacks are going to be called. If not set, the current
   *             object is used.
   * @param args the command line arguments.
   */
  public void dispatch(Object main, String[] args) {
    this.main = main == null ? this : main;
    PROCESS_ARGS = Collections.unmodifiableList(Arrays.asList(args));

    // Several cases need to be detected here.
    // The first argument may be "--help" => must display help message
    // The first argument may be "--version" => must execute the version command.
    // The first argument may be a command and the second "--help" => display command usage
    // The first argument may be a command => command execution
    // If the first argument is not a command, try to see if there is a given main verticle  and execute the default
    // command with the arguments (prepended with the main verticle).
    // Finally, we have two fallbacks
    // - if no args (and so no main verticle) - display usage
    // - if args has been set, display command usage.


    if (args.length >= 1 && isAskingForHelp(args[0])) {
      printGlobalUsage();
      return;
    }

    if (args.length >= 1 && isAskingForVersion(args[0])) {
      execute("version");
      return;
    }

    if (args.length >= 1 && commandByName.get(args[0]) != null) {
      execute(args[0], Arrays.copyOfRange(args, 1, args.length));
      return;
    }

    if (args.length >= 2 && isAskingForHelp(args[1])) {
      execute(args[0], "--help");
      return;
    }

    // We check whether or not we have a main verticle specified via the getMainVerticle method.
    // By default this method retrieve the value from the 'Main-Verticle' Manifest header. However it can be overridden.

    String verticle = getMainVerticle();
    String command = getCommandFromManifest();
    if (verticle != null) {
      // We have a main verticle, append it to the arg list and execute the default command (run)
      String[] newArgs = new String[args.length + 1];
      newArgs[0] = verticle;
      System.arraycopy(args, 0, newArgs, 1, args.length);
      execute(getDefaultCommand(), newArgs);
      return;
    } else if (command != null) {
      execute(command, args);
      return;
    }

    // Fallbacks
    if (args.length == 0) {
      printGlobalUsage();
    } else {
      // compatibility support
      if (args[0].equalsIgnoreCase("-ha")) {
        execute("bare", Arrays.copyOfRange(args, 1, args.length));
      } else {
        printCommandNotFound(args[0]);
      }
    }
  }

  /**
   * @return the default command if specified in the {@code MANIFEST}, "run" if not found.
   */
  protected String getDefaultCommand() {
    String fromManifest = getCommandFromManifest();
    if (fromManifest == null) {
      return "run";
    }
    return fromManifest;
  }

  protected String getCommandFromManifest() {
    try {
      Enumeration<URL> resources = RunCommand.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
      while (resources.hasMoreElements()) {
        InputStream stream = resources.nextElement().openStream();
        Manifest manifest = new Manifest(stream);
        Attributes attributes = manifest.getMainAttributes();
        String mainClass = attributes.getValue("Main-Class");
        if (main.getClass().getName().equals(mainClass)) {
          String command = attributes.getValue("Main-Command");
          if (command != null) {
            stream.close();
            return command;
          }
        }
        stream.close();
      }
    } catch (IOException e) {
      throw new IllegalStateException(e.getMessage());
    }
    return null;
  }

  /**
   * @return the printer used to write the messages. Defaults to {@link System#out}.
   */
  public PrintStream getPrintStream() {
    return System.out;
  }

  /**
   * @return the main verticle, {@code null} if not found.
   */
  protected String getMainVerticle() {
    try {
      Enumeration<URL> resources = RunCommand.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
      while (resources.hasMoreElements()) {
        InputStream stream = resources.nextElement().openStream();
        Manifest manifest = new Manifest(stream);
        Attributes attributes = manifest.getMainAttributes();
        String mainClass = attributes.getValue("Main-Class");
        if (main != null && main.getClass().getName().equals(mainClass)) {
          String theMainVerticle = attributes.getValue("Main-Verticle");
          if (theMainVerticle != null) {
            stream.close();
            return theMainVerticle;
          }
        }
        stream.close();
      }
    } catch (IOException e) {
      throw new IllegalStateException(e.getMessage());
    }
    return null;
  }

  /**
   * For testing purpose only - reset the process arguments
   */
  public static void resetProcessArguments() {
    PROCESS_ARGS = null;
  }
}
