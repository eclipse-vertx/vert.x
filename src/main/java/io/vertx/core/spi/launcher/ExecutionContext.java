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

package io.vertx.core.spi.launcher;

import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.impl.launcher.VertxCommandLauncher;

import java.io.PrintStream;
import java.util.HashMap;

/**
 * The execution context contains various information on the execution.
 */
public class ExecutionContext extends HashMap<String, Object> {
  private final VertxCommandLauncher launcher;
  private final Command command;
  private final CommandLine commandLine;

  /**
   * Creates a new instance of {@link ExecutionContext}.
   *
   * @param command     the command instance that is executed
   * @param launcher    the launcher class
   * @param commandLine the command line
   */
  public ExecutionContext(Command command, VertxCommandLauncher launcher, CommandLine commandLine) {
    this.command = command;
    this.commandLine = commandLine;
    this.launcher = launcher;
  }

  /**
   * @return the command line object.
   */
  public Command command() {
    return command;
  }

  /**
   * @return the launcher.
   */
  public VertxCommandLauncher launcher() {
    return launcher;
  }

  /**
   * @return the {@link CLI}.
   */
  public CLI cli() {
    return commandLine.cli();
  }

  /**
   * @return the {@link CommandLine}.
   */
  public CommandLine commandLine() {
    return commandLine;
  }

  /**
   * Executes another command.
   *
   * @param command the command name
   * @param args    the arguments
   */
  public void execute(String command, String... args) {
    launcher.execute(command, args);
  }

  /**
   * @return the {@code Main-Class} object.
   */
  public Object main() {
    return get("Main");
  }

  /**
   * @return the {@link PrintStream} on which command can write.
   */
  public PrintStream getPrintStream() {
    return launcher.getPrintStream();
  }
}
