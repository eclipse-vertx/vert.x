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
package io.vertx.core.spi.cli;

import io.vertx.core.impl.cli.VertxCommandLineInterface;

import java.io.PrintStream;
import java.util.HashMap;

/**
 * The execution context contains various information on the execution. It's mainly the built command line.
 */
public class ExecutionContext extends HashMap<String, Object> {
  private final VertxCommandLineInterface cli;
  private final CommandLine commandLine;

  public ExecutionContext(CommandLine commandLine, VertxCommandLineInterface cli) {
    this.commandLine = commandLine;
    this.cli = cli;
  }

  /**
   * @return the command line object.
   */
  public CommandLine getCommandLine() {
    return commandLine;
  }

  public VertxCommandLineInterface getInterface() {
    return cli;
  }

  /**
   * Executes another command.
   *
   * @param command the command name
   * @param args    the arguments
   */
  public void execute(String command, String... args) {
    cli.execute(command, args);
  }

  public Object main() {
    return get("Main");
  }

  /**
   * @return the {@link PrintStream} on which command can write.
   */
  public PrintStream getPrintStream() {
    return cli.getPrintStream();
  }
}
