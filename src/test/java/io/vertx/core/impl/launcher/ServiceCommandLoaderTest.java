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
package io.vertx.core.impl.launcher;

import io.vertx.core.cli.CLI;
import io.vertx.core.spi.launcher.CommandFactory;
import org.junit.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;


public class ServiceCommandLoaderTest {

  private ServiceCommandFactoryLoader loader = new ServiceCommandFactoryLoader();

  @Test
  public void testLookup() throws Exception {
    Collection<CommandFactory<?>> commands = loader.lookup();
    ensureCommand(commands, "Hello");
    ensureCommand(commands, "Bye");
  }

  private void ensureCommand(Collection<CommandFactory<?>> commands, String name) {
    List<CLI> clis = new ArrayList<>();
    for (CommandFactory command : commands) {
      CLI cli = command.define();
      clis.add(cli);
      if (cli.getName().equalsIgnoreCase(name)) {
        return;
      }
    }
    fail("Cannot find '" + name + "' in " + clis.stream().map(CLI::getName).collect(Collectors.toList()));
  }

  @Test
  public void testNoCommandsWhenLoadedFromEmptyClassloader() {
    ClassLoader classLoader = new URLClassLoader(new URL[0], null);

    // We see the implementation from the classpath
    loader = new ServiceCommandFactoryLoader(classLoader);
    assertThat(loader.lookup()).isNotEmpty();
  }


  @Test
  public void testCommandsWhenUsingClassloaderHierarchy() {
    ClassLoader classLoader = new URLClassLoader(new URL[0], ServiceCommandLoaderTest.class.getClassLoader());
    loader = new ServiceCommandFactoryLoader(classLoader);
    Collection<CommandFactory<?>> commands = loader.lookup();
    ensureCommand(commands, "Hello");
    ensureCommand(commands, "Bye");
  }
}