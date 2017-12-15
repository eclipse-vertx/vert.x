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

import io.vertx.core.Launcher;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;

/**
 * SPI Interface to provide a new {@link Launcher} command. Implementors needs to provide two methods:
 * <ol>
 * <li>{@link #define()} - creates a {@link CLI} instance (so the model)</li>
 * <li>{@link #create(CommandLine)}} - creates a new command instance</li>
 * </ol>
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public interface CommandFactory<C extends Command> {

  /**
   * @return a new instance of the command.
   */
  C create(CommandLine evaluated);

  /**
   * Creates a new {@link CLI} instance.
   *
   * @return the CLI.
   */
  CLI define();


}
