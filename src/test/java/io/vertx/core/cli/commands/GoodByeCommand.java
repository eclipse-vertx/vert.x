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
package io.vertx.core.cli.commands;

import io.vertx.core.cli.*;

import java.util.List;

@Summary("A command saying goodbye.")
public class GoodByeCommand extends DefaultCommand {

  /**
   * @return the command name such as 'run'.
   */
  @Override
  public String name() {
    return "bye";
  }

  @Override
  public List<OptionModel> options() {
    List<OptionModel> list = super.options();
    list.add(OptionModel.<String>builder()
        .isRequired()
        .shortName("n")
        .acceptValue()
        .type(String.class)
        .build());
    return list;
  }

  /**
   * Executes the command.
   *
   * @throws CommandLineException If anything went wrong.
   */
  @Override
  public void run() throws CommandLineException {
    final String n = executionContext.getCommandLine().getOptionValue("n");
    out.println("Good Bye " + n);
  }
}
