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

import io.vertx.core.spi.cli.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A command starting a vert.x application in the background.
 */
@Summary("Stop a vert.x application")
@Description("This command stops a vert.x application started with the `start` command. The command requires the application id as argument. Use the `list` command to get the list of application")
public class StopCommand extends DefaultCommand {

  private String id;

  private static final Pattern PS = Pattern.compile("([0-9]+)\\s.*-Dvertx.id=.*");

  @Argument(index = 0, name = "application-id", required = false)
  @Description("The application id")
  public void setApplicationId(String id) {
    this.id = id;
  }

  @Override
  public String name() {
    return "stop";
  }

  /**
   * Executes the command.
   *
   * @throws CommandLineException If anything went wrong.
   */
  @Override
  public void run() throws CommandLineException {
    if (id == null) {
      out.println("Application id not specified... Retrieving vert.x applications");
      executionContext.execute("list");
      return;
    }

    out.println("Stopping vert.x application '" + id + "'");
    if (ExecUtils.isWindows()) {
      terminateWindowsApplication();
    } else {
      terminateLinuxApplication();
    }
  }

  private void terminateLinuxApplication() {
    String pid = pid();
    if (pid == null) {
      out.println("Cannot find process for application id " + id);
      return;
    }

    List<String> cmd = new ArrayList<>();
    cmd.add("kill");
    cmd.add(pid);
    try {
      new ProcessBuilder(cmd).start().waitFor();
      out.println("Application '" + id + "' stopped");
    } catch (Exception e) {
      out.println("Failed to stop application '" + id + "'");
      e.printStackTrace(out);
    }
  }

  private void terminateWindowsApplication() {
    List<String> cmd = new ArrayList<>();
    // Use wmic.
    cmd.add("WMIC");
    cmd.add("PROCESS");
    cmd.add("WHERE");
    cmd.add("CommandLine like '%vertx.id=" + id + "%'");
    cmd.add("CALL");
    cmd.add("TERMINATE");

    try {
      final Process process = new ProcessBuilder(cmd).start();
      out.println("Application '" + id + "' stopped");
      process.waitFor();
    } catch (Exception e) {
      out.println("Failed to stop application '" + id + "'");
      e.printStackTrace(out);
    }

  }

  private String pid() {
    try {
      List<String> cmd = new ArrayList<>();
      cmd.add("sh");
      cmd.add("-c");
      cmd.add("ps ax | grep \"" + id + "\"");

      final Process process = new ProcessBuilder(cmd).start();
      BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream()));
      String line;
      while ((line = reader.readLine()) != null) {
        final Matcher matcher = PS.matcher(line);
        if (matcher.find()) {
          return matcher.group(1);
        }
      }
      process.waitFor();
    } catch (Exception e) {
      e.printStackTrace(out);
    }
    return null;
  }
}
