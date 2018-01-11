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

package io.vertx.core.impl.launcher.commands;

import io.vertx.core.cli.annotations.*;
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

  /**
   * Whether or not we are in redeploy mode. In redeploy mode, do not exit the VM.
   */
  private boolean redeploy;


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

  @Option(longName = "redeploy", flag = true)
  @Hidden
  public void setRedeploy(boolean redeploy) {
    this.redeploy = redeploy;
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
      if (!redeploy) {
        ExecUtils.exitBecauseOfProcessIssue();
      }
      return;
    }

    List<String> cmd = new ArrayList<>();
    cmd.add("kill");
    cmd.add(pid);
    try {
      int result = new ProcessBuilder(cmd).start().waitFor();
      out.println("Application '" + id + "' terminated with status " + result);
      if (!redeploy) {
        // We leave the application using the same exit code.
        ExecUtils.exit(result);
      }
    } catch (Exception e) {
      out.println("Failed to stop application '" + id + "'");
      e.printStackTrace(out);
      if (!redeploy) {
        ExecUtils.exitBecauseOfProcessIssue();
      }
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
      int result = process.waitFor();
      out.println("Application '" + id + "' terminated with status " + result);
      if (!redeploy) {
        // We leave the application using the same exit code.
        ExecUtils.exit(result);
      }
    } catch (Exception e) {
      out.println("Failed to stop application '" + id + "'");
      e.printStackTrace(out);
      if (!redeploy) {
        ExecUtils.exitBecauseOfProcessIssue();
      }
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
