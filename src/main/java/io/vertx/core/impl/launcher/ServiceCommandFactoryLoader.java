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

package io.vertx.core.impl.launcher;

import io.vertx.core.ServiceHelper;
import io.vertx.core.spi.launcher.CommandFactory;
import io.vertx.core.spi.launcher.CommandFactoryLookup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Looks for command factories using a service loader.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class ServiceCommandFactoryLoader implements CommandFactoryLookup {

  private Collection<CommandFactory> commands;

  /**
   * Creates a new instance of {@link ServiceCommandFactoryLoader} using the classloader having loaded the
   * {@link ServiceCommandFactoryLoader} class.
   */
  public ServiceCommandFactoryLoader() {
    this.commands = ServiceHelper.loadFactories(CommandFactory.class, getClass().getClassLoader());
  }

  /**
   * Creates a new instance of {@link ServiceCommandFactoryLoader} using specified classloader.
   */
  public ServiceCommandFactoryLoader(ClassLoader loader) {
    this.commands = ServiceHelper.loadFactories(CommandFactory.class, loader);
  }

  @Override
  public Collection<CommandFactory<?>> lookup() {
    List<CommandFactory<?>> list = new ArrayList<>();
    commands.stream().forEach(list::add);
    return list;
  }

}
