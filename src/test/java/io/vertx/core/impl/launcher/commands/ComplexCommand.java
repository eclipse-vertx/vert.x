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

import io.vertx.core.cli.*;
import io.vertx.core.cli.annotations.*;
import io.vertx.core.cli.annotations.Argument;
import io.vertx.core.cli.annotations.Option;
import io.vertx.core.spi.launcher.DefaultCommand;

@Summary("A command with options and arguments.")
@Description("This is a complex command.")
@Name("complex")
public class ComplexCommand extends DefaultCommand {

  private String arg1;
  private int arg2;
  private String option1;
  private boolean option2;

  @Argument(index = 0, argName = "arg1")
  public void setArgument1(String arg1) {
    this.arg1 = arg1;
  }

  @Argument(index = 1, argName = "arg2", required = false)
  public void setArgument2(int arg2) {
    this.arg2 = arg2;
  }

  @Option(
      longName = "option1",
      shortName = "o1",
      argName = "opt",
      required = true
  )
  public void setOption1(String option1) {
    this.option1 = option1;
  }

  @Option(
      longName = "option2",
      shortName = "o2",
      acceptValue = false,
      required = false,
      flag = true
  )
  public void setOption2(boolean option2) {
    this.option2 = option2;
  }


  @Override
  public void run() throws CLIException {
    out.println("Option 1 : " + option1);
    out.println("Option 2 : " + option2);
    out.println("Arg 1 : " + arg1);
    out.println("Arg 2 : " + arg2);
    if (executionContext.commandLine().allArguments().size() > 2) {
      out.println("All args: " + executionContext.commandLine().allArguments());
    }
  }
}
