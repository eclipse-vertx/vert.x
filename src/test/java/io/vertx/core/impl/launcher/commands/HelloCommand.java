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

import io.vertx.core.cli.*;
import io.vertx.core.cli.annotations.Description;
import io.vertx.core.cli.annotations.Name;
import io.vertx.core.cli.annotations.Option;
import io.vertx.core.cli.annotations.Summary;
import io.vertx.core.spi.launcher.DefaultCommand;

@Summary("A command saying hello.")
@Description("A simple command to wish you a good day. Pass your name with `--name`")
@Name("hello")
public class HelloCommand extends DefaultCommand {

  public static boolean called = false;

  public String name;


  @Option(longName = "name", shortName = "n", required = true, argName = "name")
  @Description("your name")
  public void setTheName(String name) {
    this.name = name;
  }

  /**
   * Executes the command.
   *
   * @throws CLIException If anything went wrong.
   */
  @Override
  public void run() throws CLIException {
    called = true;
    out.println("Hello " + name);
  }
}
