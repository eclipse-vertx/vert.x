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

  @Override
  public void run() throws CLIException {
    log.info(getVersion());
  }

  /**
   * Reads the version from the {@code vertx-version.txt} file.
   *
   * @return the version
   */
  public String getVersion() {
    try (InputStream is = getClass().getClassLoader().getResourceAsStream("vertx-version.txt")) {
      if (is == null) {
        throw new IllegalStateException("Cannot find vertx-version.txt on classpath");
      }
      try (Scanner scanner = new Scanner(is, "UTF-8").useDelimiter("\\A")) {
        return scanner.hasNext() ? scanner.next() : "";
      }
    } catch (IOException e) {
      throw new IllegalStateException(e.getMessage());
    }
  }
}
