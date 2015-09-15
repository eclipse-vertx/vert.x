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
