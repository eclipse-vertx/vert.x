/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 and the Apache License, Version 2.0
 * which accompanies this distribution. The Eclipse Public License 2.0 is
 * available at http://www.eclipse.org/legal/epl-2.0.html, and the Apache
 * License, Version 2.0 is available at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
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
