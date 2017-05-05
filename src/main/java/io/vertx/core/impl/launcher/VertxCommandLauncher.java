/*
 *  Copyright (c) 2011-2015 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *       The Eclipse Public License is available at
 *       http://www.eclipse.org/legal/epl-v10.html
 *
 *       The Apache License v2.0 is available at
 *       http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
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
// TODO: 16/12/12 by zmyer
public class VertxCommandLauncher extends UsageMessageFormatter {

    //指令参数集合
    protected static List<String> PROCESS_ARGS;

    /**
     * @return the process argument. Verticles can use this method to retrieve the arguments.
     */
    // TODO: 16/12/12 by zmyer
    public static List<String> getProcessArguments() {
        return PROCESS_ARGS;
    }

    /**
     * the list of lookups.
     */
    //查询集合
    protected final List<CommandFactoryLookup> lookups;

    /**
     * the list of commands. Sub-classes can decide to remove commands by removing entries from this map.
     */
    //指令注册映射表
    protected final Map<String, CommandRegistration> commandByName;

    /**
     * the {@code Main-Class} object.
     */
    //主函数对象
    protected Object main;

    /**
     * Handles a command registration.
     */
    //指令注册类
    public static class CommandRegistration {
        //指令工厂对象
        public final CommandFactory factory;
        //指令客户端
        public final CLI cli;
        //指令集合
        private List<Command> commands = new ArrayList<>();

        public CommandRegistration(CommandFactory factory) {
            this(factory, factory.define());
        }

        public CommandRegistration(CommandFactory factory, CLI cli) {
            this.factory = factory;
            this.cli = cli;
        }

        // TODO: 16/12/13 by zmyer
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
    // TODO: 16/12/13 by zmyer
    public VertxCommandLauncher() {
        this(Collections.singletonList(new ServiceCommandFactoryLoader()));
    }

    /**
     * Creates a new {@link VertxCommandLauncher} using the given list of {@link CommandFactoryLookup}.
     *
     * @param lookups the list of lookup
     */
    // TODO: 16/12/13 by zmyer
    public VertxCommandLauncher(Collection<CommandFactoryLookup> lookups) {
        this.lookups = new ArrayList<>(lookups);
        this.commandByName = new TreeMap<>();
        load();
    }

    /**
     * Loads the command. This method is {@link protected} to let sub-classes change the set of command or how
     * they are loaded.
     */
    // TODO: 16/12/13 by zmyer
    protected void load() {
        //依次遍历每个指令工厂查找对象
        for (CommandFactoryLookup lookup : lookups) {
            //从指令查找对象中读取指令集合
            Collection<CommandFactory<?>> commands = lookup.lookup();
            //开始注册每个指令
            commands.forEach(this::register);
        }
    }

    // TODO: 16/12/13 by zmyer
    public VertxCommandLauncher register(CommandFactory factory) {
        CLI cli = factory.define();
        commandByName.put(cli.getName(), new CommandRegistration(factory, cli));
        return this;
    }

    // TODO: 16/12/13 by zmyer
    @SuppressWarnings("unchecked")
    public VertxCommandLauncher register(Class<? extends Command> clazz) {
        //构造默认命令工厂对象
        DefaultCommandFactory factory = new DefaultCommandFactory(clazz);
        //创建客户端
        CLI cli = factory.define();
        //注册
        commandByName.put(cli.getName(), new CommandRegistration(factory, cli));
        return this;
    }

    // TODO: 16/12/13 by zmyer
    public VertxCommandLauncher unregister(String name) {
        commandByName.remove(name);
        return this;
    }

    /**
     * @return the list of command.
     */
    // TODO: 16/12/13 by zmyer
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
    // TODO: 16/12/13 by zmyer
    protected Command getNewCommandInstance(String name, CommandLine commandLine) {
        //根据指令的名称,获取对应的指令注册对象
        CommandRegistration registration = commandByName.get(name);
        if (registration != null) {
            //开始针对命令行,创建对应的指令对象
            Command command = registration.factory.create(commandLine);
            //将指令对象加入到命令注册对象中
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
    // TODO: 16/12/13 by zmyer
    public Command getExistingCommandInstance(String name) {
        //首先获取指令注册对象
        CommandRegistration registration = commandByName.get(name);
        if (registration != null) {
            //从指令注册对象中读取指令对象
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
    // TODO: 16/12/13 by zmyer
    public void execute(String command, String... cla) {
        //如果是询问版本指令
        if (command != null && isAskingForVersion(command)) {
            //直接运行版本指令
            execute("version");
            return;
        }

        //如果是帮助指令
        if (command == null || isAskingForHelp(command)) {
            //打印帮助信息
            printGlobalUsage();
            return;
        }

        //根据提供的指令名称,读取指令注册对象
        CommandRegistration registration = commandByName.get(command);
        if (registration == null) {
            //打印未查找到相关指令对象
            printCommandNotFound(command);
            return;
        }

        //从指令注册对象中读取对应的客户端对象
        CLI cli = registration.cli;

        try {
            // Check for help - the command need to have been initialized ot get the complete model.
            if (cla.length >= 1 && isAskingForHelp(cla[0])) {
                printCommandUsage(cli);
                return;
            }

            // Step 1 - parsing and injection
            //首先需要解析指令参数
            CommandLine evaluated = cli.parse(Arrays.asList(cla));
            //根据构造的命令行对象,创建指令对象
            Command cmd = getNewCommandInstance(command, evaluated);
            //根据指令对象,构造执行上下文对象
            ExecutionContext context = new ExecutionContext(cmd, this, evaluated);
            if (main != null) {
                //如果是主函数,则需要将其注册到上下文对象中
                context.put("Main", main);
                //注册主函数类对象
                context.put("Main-Class", main.getClass().getName());
            }
            //开始注入指令对象
            CLIConfigurator.inject(evaluated, cmd);

            // Step 2 - validation
            //设置指令对象执行上下文对象
            cmd.setUp(context);

            // Step 3 - execution
            //开始执行指令
            cmd.run();

            // Step 4 - cleanup
            //清理
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

    // TODO: 16/12/13 by zmyer
    protected void printCommandUsage(CLI cli) {
        StringBuilder builder = new StringBuilder();
        cli.usage(builder, getCommandLinePrefix());
        getPrintStream().println(builder.toString());
    }

    // TODO: 16/12/13 by zmyer
    protected void printGenericExecutionError(CLI cli, CLIException e) {
        getPrintStream().println("Error while executing command " + cli.getName() + ": " + e.getMessage() + getNewLine());
        if (e.getCause() != null) {
            e.getCause().printStackTrace(getPrintStream());
        }
    }

    // TODO: 16/12/13 by zmyer
    protected void printSpecificException(CLI cli, Exception e) {
        getPrintStream().println(e.getMessage() + getNewLine());
        printCommandUsage(cli);
    }

    // TODO: 16/12/13 by zmyer
    protected void printCommandNotFound(String command) {
        StringBuilder builder = new StringBuilder();
        buildWrapped(builder, 0, "The command '" + command + "' is not a valid command." + getNewLine()
                + "See '" + getCommandLinePrefix() + " --help'");
        getPrintStream().println(builder.toString());
    }

    // TODO: 16/12/13 by zmyer
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

    // TODO: 16/12/13 by zmyer
    protected String getCommandLinePrefix() {
        // Check whether `vertx.cli.usage.prefix` is set, if so use it. This system property let scripts configure the value
        // displayed by the usage, even if they are calling java.
        //读取指令前缀
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

    // TODO: 16/12/13 by zmyer
    protected static boolean isAskingForHelp(String command) {
        return command.equalsIgnoreCase("--help")
                || command.equalsIgnoreCase("-help")
                || command.equalsIgnoreCase("-h")
                || command.equalsIgnoreCase("?")
                || command.equalsIgnoreCase("/?");
    }

    // TODO: 16/12/13 by zmyer
    protected static boolean isAskingForVersion(String command) {
        return command.equalsIgnoreCase("-version") || command.equalsIgnoreCase("--version");
    }

    /**
     * Dispatches to the right command. This method is generally called from the {@code main} method.
     *
     * @param args the command line arguments.
     */
    // TODO: 16/12/13 by zmyer
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
    // TODO: 16/12/13 by zmyer
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

        //开始执行指令
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
    // TODO: 16/12/13 by zmyer
    protected String getDefaultCommand() {
        String fromManifest = getCommandFromManifest();
        if (fromManifest == null) {
            return "run";
        }
        return fromManifest;
    }

    // TODO: 16/12/13 by zmyer
    protected String getCommandFromManifest() {
        try {
            //读取资源列表
            Enumeration<URL> resources = RunCommand.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                //依次读取每个资源文件
                InputStream stream = resources.nextElement().openStream();
                Manifest manifest = new Manifest(stream);
                //主函数属性信息
                Attributes attributes = manifest.getMainAttributes();
                //读取主函数类信息
                String mainClass = attributes.getValue("Main-Class");
                if (main.getClass().getName().equals(mainClass)) {
                    //从中读取指令对象
                    String command = attributes.getValue("Main-Command");
                    if (command != null) {
                        stream.close();
                        //返回指令对象
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
    // TODO: 16/12/13 by zmyer
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
