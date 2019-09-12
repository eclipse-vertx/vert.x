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
import io.vertx.core.cli.annotations.CLIConfigurator;
import io.vertx.core.cli.impl.ReflectionUtils;

import java.util.function.Supplier;

/**
 * Default implementation of {@link CommandFactory}. This implementation defines the {@link CLI} from the
 * given {@link Command} implementation (by reading the annotation). Then, {@link Command} instance are
 * created by calling an empty constructor on the given {@link Command} implementation.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class DefaultCommandFactory<C extends Command> implements CommandFactory<C> {

  private final Class<C> clazz;
  private final Supplier<C> supplier;

  /**
   * Creates a new {@link CommandFactory}.
   *
   * @param clazz the {@link Command} implementation
   * @deprecated Please use {@link #DefaultCommandFactory(Class, Supplier)}
   */
  @Deprecated
  public DefaultCommandFactory(Class<C> clazz) {
    this(clazz, () -> ReflectionUtils.newInstance(clazz));
  }

  /**
   * Creates a new {@link CommandFactory}.
   *
   * @param clazz the {@link Command} implementation
   * @param supplier the {@link Command} implementation
   */
  public DefaultCommandFactory(Class<C> clazz, Supplier<C> supplier) {
    this.clazz = clazz;
    this.supplier = supplier;
  }

  /**
   * @return a new instance of the command by invoking the default constructor of the given class.
   */
  @Override
  public C create(CommandLine cl) {
    return supplier.get();
  }

  /**
   * @return the {@link CLI} instance by reading the annotation.
   */
  @Override
  public CLI define() {
    return CLIConfigurator.define(clazz);
  }
}
