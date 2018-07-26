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

import io.vertx.core.cli.CLIException;
import io.vertx.core.cli.annotations.Description;
import io.vertx.core.cli.annotations.Name;
import io.vertx.core.cli.annotations.Summary;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.launcher.DefaultCommand;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

/**
 * Comment to display the vert.x (core) version.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
@Name("version")
@Summary("Displays the version.")
@Description("Prints the vert.x core version used by the application.")
public class VersionCommand extends DefaultCommand {

  private static final Logger log = LoggerFactory.getLogger(VersionCommand.class);

  private static String version;

  @Override
  public void run() throws CLIException {
    log.info(getVersion());
  }

  /**
   * Reads the version from the {@code vertx-version.txt} file.
   *
   * @return the version
   */
  public static String getVersion() {
    if (version != null) {
      return version;
    }
    try (InputStream is = VersionCommand.class.getClassLoader().getResourceAsStream("META-INF/vertx/vertx-version.txt")) {
      if (is == null) {
        throw new IllegalStateException("Cannot find vertx-version.txt on classpath");
      }
      try (Scanner scanner = new Scanner(is, "UTF-8").useDelimiter("\\A")) {
        return version = scanner.hasNext() ? scanner.next().trim() : "";
      }
    } catch (IOException e) {
      throw new IllegalStateException(e.getMessage());
    }
  }
}
