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

import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.annotations.CLIConfigurator;
import io.vertx.core.cli.impl.ReflectionUtils;

/**
 * Default implementation of {@link CommandFactory}. This implementation defines the {@link CLI} from the
 * given {@link Command} implementation (by reading the annotation). Then, {@link Command} instance are
 * created by calling an empty constructor on the given {@link Command} implementation.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class DefaultCommandFactory<C extends Command> implements CommandFactory<C> {

  private final Class<C> clazz;

  /**
   * Creates a new {@link CommandFactory}.
   *
   * @param clazz the {@link Command} implementation
   */
  public DefaultCommandFactory(Class<C> clazz) {
    this.clazz = clazz;
  }

  /**
   * @return a new instance of the command by invoking the default constructor of the given class.
   */
  @Override
  public C create(CommandLine cl) {
    C c = ReflectionUtils.newInstance(clazz);
    CLIConfigurator.inject(cl, c);
    return c;
  }

  /**
   * @return the {@link CLI} instance by reading the annotation.
   */
  @Override
  public CLI define() {
    return CLIConfigurator.define(clazz);
  }
}
