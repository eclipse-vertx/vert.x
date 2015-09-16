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

package io.vertx.core.impl.launcher.commands;


import io.vertx.core.cli.annotations.*;
import io.vertx.core.impl.launcher.CommandLineUtils;
import io.vertx.core.spi.launcher.DefaultCommand;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * A command starting a vert.x application in the background.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
@Name("start")
@Summary("Start a vert.x application in background")
@Description("Start a vert.x application as a background service. The application is identified with an id that can be set using the `vertx-id` option. If not set a random UUID is generated. The application can be stopped with the `stop` command.")
public class StartCommand extends DefaultCommand {

  private String id;
  private String launcher;

  private boolean redirect;
  private String jvmOptions;

  /**
   * Sets the "application id" that would be to stop the application and be lsited in the {@code list} command.
   *
   * @param id the id
   */
  @Option(longName = "vertx-id", shortName = "id", required = false, acceptValue = true)
  @Description("The id of the application, a random UUID by default")
  public void setApplicationId(String id) {
    this.id = id;
  }

  /**
   * Sets the Java Virtual Machine options to pass to the spawned process. If not set, the JAVA_OPTS environment
   * variable is used.
   *
   * @param options the jvm options
   */
  @Option(longName = "java-opts", required = false, acceptValue = true)
  @Description("Java Virtual Machine options to pass to the spawned process such as \"-Xmx1G -Xms256m " +
      "-XX:MaxPermSize=256m\". If not set the `JAVA_OPTS` environment variable is used.")
  public void setJavaOptions(String options) {
    this.jvmOptions = options;
  }

  /**
   * A hidden option to set the launcher class.
   *
   * @param clazz the class
   */
  @Option(longName = "launcher-class")
  @Hidden
  public void setLauncherClass(String clazz) {
    this.launcher = clazz;
  }

  /**
   * Whether or not the created process error streams and output streams needs to be redirected to the launcher process.
   *
   * @param redirect {@code true} to enable redirection, {@code false} otherwise
   */
  @Option(longName = "redirect-output", flag = true)
  @Hidden
  public void setRedirect(boolean redirect) {
    this.redirect = redirect;
  }

  /**
   * Starts the application in background.
   */
  @Override
  public void run() {
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
      ExecUtils.addArgument(cmd, CommandLineUtils.getJar());
    } else {
      // probably a `vertx` command line usage, or in IDE.
      ExecUtils.addArgument(cmd, CommandLineUtils.getFirstSegmentOfCommand());
      ExecUtils.addArgument(cmd, "run");
    }

    getArguments().stream().forEach(arg -> ExecUtils.addArgument(cmd, arg));

    try {
      builder.command(cmd);
      if (redirect) {
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
      }
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
      ExecUtils.addArgument(cmd, "vertx-id - " + id);
      ExecUtils.addArgument(cmd, "/B");
    }
    ExecUtils.addArgument(cmd, getJava().getAbsolutePath());

    // Compute JVM Options
    if (jvmOptions == null) {
      String opts = System.getenv("JAVA_OPTS");
      if (opts != null) {
        Arrays.stream(opts.split(" ")).forEach(s -> ExecUtils.addArgument(cmd, s));
      }
    } else {
      Arrays.stream(jvmOptions.split(" ")).forEach(s -> ExecUtils.addArgument(cmd, s));
    }
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
    return CommandLineUtils.getJar() != null;
  }

  private List<String> getArguments() {
    List<String> args = executionContext.commandLine().allArguments();
    // Add system properties passed as parameter
    if (systemProperties != null) {
      systemProperties.stream().map(entry -> "-D" + entry).forEach(args::add);
    }

    // Add id - it's important as it's the application mark.
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