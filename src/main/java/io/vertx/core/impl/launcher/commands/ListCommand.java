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


import io.vertx.core.cli.annotations.Description;
import io.vertx.core.cli.annotations.Name;
import io.vertx.core.cli.annotations.Summary;
import io.vertx.core.spi.launcher.DefaultCommand;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A command listing launched vert.x instances. Instances are found using the `vertx.id` indicator in the
 * process list.
 */
@Name("list")
@Summary("List vert.x applications")
@Description("List all vert.x applications launched with the `start` command")
public class ListCommand extends DefaultCommand {

  private final static Pattern PS = Pattern.compile("-Dvertx.id=(.*)\\s*");

  private final static Pattern FAT_JAR_EXTRACTION = Pattern.compile("-jar (\\S*)");

  private final static Pattern VERTICLE_EXTRACTION = Pattern.compile("run (\\S*)");

  // Note about stack traces - the stack trace are printed on the stream passed to the command.

  /**
   * Executes the {@code list} command.
   */
  @Override
  public void run() {
    out.println("Listing vert.x applications...");
    List<String> cmd = new ArrayList<>();
    if (!ExecUtils.isWindows()) {
      try {
        cmd.add("sh");
        cmd.add("-c");
        cmd.add("ps ax | grep \"vertx.id=\"");

        dumpFoundVertxApplications(cmd);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        e.printStackTrace(out);
      } catch (Exception e) {
        e.printStackTrace(out);
      }

    } else {
      try {
        // Use wmic.
        cmd.add("WMIC");
        cmd.add("PROCESS");
        cmd.add("WHERE");
        cmd.add("CommandLine like '%java.exe%'");
        cmd.add("GET");
        cmd.add("CommandLine");
        cmd.add("/VALUE");

        dumpFoundVertxApplications(cmd);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        e.printStackTrace(out);
      } catch (Exception e) {
        e.printStackTrace(out);
      }
    }


  }

  private void dumpFoundVertxApplications(List<String> cmd) throws IOException, InterruptedException {
    boolean none = true;
    final Process process = new ProcessBuilder(cmd).start();
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream()));
    String line;
    while ((line = reader.readLine()) != null) {
      final Matcher matcher = PS.matcher(line);
      if (matcher.find()) {
        String id = matcher.group(1);
        String details = extractApplicationDetails(line);
        out.println(id + "\t" + details);
        none = false;
      }
    }
    process.waitFor();
    reader.close();
    if (none) {
      out.println("No vert.x application found.");
    }
  }

  /**
   * Tries to extract the fat jar name of the verticle name. It's a best-effort approach looking at the name of the
   * jar or to the verticle name from the command line. If not found, no details are returned (empty string).
   *
   * @return the details, empty if it cannot be extracted.
   */
  protected static String extractApplicationDetails(String line) {
    Matcher matcher = FAT_JAR_EXTRACTION.matcher(line);
    if (matcher.find()) {
      return matcher.group(1);
    } else {
      matcher = VERTICLE_EXTRACTION.matcher(line);
      if (matcher.find()) {
        return matcher.group(1);
      }
    }
    // No details.
    return "";
  }
}