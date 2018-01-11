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
import io.vertx.core.cli.annotations.Hidden;
import io.vertx.core.cli.annotations.Name;
import io.vertx.core.cli.annotations.Option;
import io.vertx.core.spi.launcher.DefaultCommand;

@Hidden
@Name("hidden")
public class HiddenCommand extends DefaultCommand {

  public String name;
  private int count;


  @Option(longName = "name", shortName = "n", required = true)
  public void setAName(String name) {
    this.name = name;
  }

  @Option(longName = "count", shortName = "c")
  @Hidden
  public void setCount(int count) {
    this.count = count;
  }


  /**
   * Executes the command.
   *
   * @throws CLIException If anything went wrong.
   */
  @Override
  public void run() throws CLIException {
    out.println("Do something hidden...");
  }
}
