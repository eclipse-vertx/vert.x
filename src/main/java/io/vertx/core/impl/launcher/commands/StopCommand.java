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

import io.vertx.core.cli.annotations.Argument;
import io.vertx.core.cli.annotations.Description;
import io.vertx.core.cli.annotations.Name;
import io.vertx.core.cli.annotations.Summary;
import io.vertx.core.spi.launcher.DefaultCommand;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A command stopping a vert.x application launched using the `start` command.  The application is
 * identified by its id.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
@Name("stop")
@Summary("Stop a vert.x application")
@Description("This command stops a vert.x application started with the `start` command. The command requires the " +
    "application id as argument. Use the `list` command to get the list of applications")
public class StopCommand extends DefaultCommand {

  private String id;

  private static final Pattern PS = Pattern.compile("([0-9]+)\\s.*-Dvertx.id=.*");

  /**
   * As the {@code stop} command takes only a single argument, it's the application id.
   *
   * @param id the id.
   */
  @Argument(index = 0, argName = "vertx.id", required = false)
  @Description("The vert.x application id")
  public void setApplicationId(String id) {
    this.id = id;
  }

  /**
   * Stops a running vert.x application launched with the `start` command.
   */
  @Override
  public void run() {
    if (id == null) {
      out.println("Application id not specified...");
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
      out.println("Cannot find process for application using the id '" + id + "'.");
      return;
    }

    List<String> cmd = new ArrayList<>();
    cmd.add("kill");
    cmd.add(pid);
    try {
      new ProcessBuilder(cmd).start().waitFor();
      out.println("Application '" + id + "' stopped.");
    } catch (Exception e) {
      out.println("Failed to stop application '" + id + "'");
      e.printStackTrace(out);
    }
  }

  private void terminateWindowsApplication() {
    // Use wmic.
    List<String> cmd = Arrays.asList(
        "WMIC",
        "PROCESS",
        "WHERE",
        "CommandLine like '%vertx.id=" + id + "%'",
        "CALL",
        "TERMINATE"
    );

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
      final Process process = new ProcessBuilder(Arrays.asList("sh", "-c", "ps ax | grep \"" + id + "\"")).start();
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
      reader.close();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      e.printStackTrace(out);
    } catch (Exception e) {
      e.printStackTrace(out);
    }
    return null;
  }
}