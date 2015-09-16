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

package io.vertx.core.spi.launcher;

import io.vertx.core.cli.CLIException;
import io.vertx.core.cli.annotations.Argument;
import io.vertx.core.cli.annotations.Description;
import io.vertx.core.cli.annotations.Option;
import io.vertx.core.cli.annotations.Summary;

/**
 * A plug-in to the Vert.x command or {@link io.vertx.core.Launcher} class. Each command instance is created
 * by a {@link CommandFactory}.
 * <p/>
 * {@link Command} implementation can retrieve argument and option using the {@link Argument} and {@link
 * Option} annotations. Documentation / help is provided using the {@link Summary} (single sentence) and
 * {@link Description} annotations.
 * <p>
 * Commands follow a strict lifecycle. The {@link #setUp(ExecutionContext)} method is called with an
 * execution context. It lets you validate the inputs and prepare the environment is needed. The
 * {@link #run()} method is called immediately after {@link #setUp(ExecutionContext)}, and executes the
 * command. Finally, once the command has completed, the {@link #tearDown()} method is called. In this method
 * you have the opportunity to cleanup.
 * </p>
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public interface Command {

  /**
   * Set up the command execution environment.
   * The command line model has been retrieved and is frozen. Values has been set / injected. You can use
   * this callback to validate the inputs.
   *
   * @param context the context
   * @throws CLIException if the validation failed
   */
  void setUp(ExecutionContext context) throws CLIException;

  /**
   * Executes the command.
   *
   * @throws CLIException If anything went wrong.
   */
  void run() throws CLIException;

  /**
   * The command has been executed. Use this method to cleanup the environment.
   *
   * @throws CLIException if anything went wrong
   */
  void tearDown() throws CLIException;

}
