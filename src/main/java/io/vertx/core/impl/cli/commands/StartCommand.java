/*
 *  Copyright (c) 2011-2013 The original author or authors
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

package io.vertx.core.impl.cli.commands;


import io.vertx.core.impl.cli.ReflectionUtils;
import io.vertx.core.spi.cli.*;

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

  private String id;
  private String launcher;


  @Option(longName = "vertx.id", shortName = "id", required = false, acceptValue = true)
  @Description("The id of the application, a random UUID by default")
  public void setApplicationId(String id) {
    this.id = id;
  }

  @Option(longName = "launcher-class")
  @Hidden
  public void setLauncherClass(String clazz) {
    this.launcher = clazz;
  }

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
    ProcessBuilder builder = new ProcessBuilder();
    addJavaCommand(cmd);

    // Add the classpath to env.
    builder.environment().put("CLASSPATH", System.getProperty("java.class.path"));

    if (launcher != null) {
      ExecUtils.addArgument(cmd, launcher);
    } else if (isLaunchedAsFatJar()) {
      ExecUtils.addArgument(cmd, "-jar");
      ExecUtils.addArgument(cmd, ReflectionUtils.getJar());
    } else {
      ExecUtils.addArgument(cmd, ReflectionUtils.getFirstSegmentOfCommand());
    }

    getArguments().stream().forEach(arg -> ExecUtils.addArgument(cmd, arg));

    try {
      builder.command(cmd);
      builder.start();
      out.println(id);
    } catch (IOException e) {
      out.println("Cannot create vert.x application process");
      e.printStackTrace(out);
    }

  }

  private void addJavaCommand(List<String> cmd) {
    if (ExecUtils.isWindows()) {
      ExecUtils.addArgument(cmd, "cmd.exe");
      ExecUtils.addArgument(cmd, "/C");
      ExecUtils.addArgument(cmd, "start");
      ExecUtils.addArgument(cmd, "vertx.id - " + id);
      ExecUtils.addArgument(cmd, "/B");
    }
    ExecUtils.addArgument(cmd, getJava().getAbsolutePath());
  }

  private File getJava() {
    File java;
    File home = new File(System.getProperty("java.home"));
    if (ExecUtils.isWindows()) {
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
}
