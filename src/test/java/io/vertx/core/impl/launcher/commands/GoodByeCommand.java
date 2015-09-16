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

import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CLIException;
import io.vertx.core.cli.impl.DefaultCLI;
import io.vertx.core.cli.TypedOption;
import io.vertx.core.spi.launcher.DefaultCommand;

public class GoodByeCommand extends DefaultCommand {

  public static CLI define() {
    return new DefaultCLI().setName("bye").addOption(new TypedOption<String>()
        .setType(String.class)
        .setRequired(true)
        .setShortName("n")
        .setSingleValued(true))
        .setSummary("A command saying goodbye.");
  }


  /**
   * Executes the command.
   *
   * @throws CLIException If anything went wrong.
   */
  @Override
  public void run() throws CLIException {
    final String n = executionContext.commandLine().getOptionValue("n");
    out.println("Good Bye " + n);
  }
}
