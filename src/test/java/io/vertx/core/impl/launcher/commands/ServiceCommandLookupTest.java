/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 *     The Apache License v2.0 is available at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.impl.launcher.commands;

import io.vertx.core.cli.CLI;
import io.vertx.core.impl.launcher.ServiceCommandFactoryLoader;
import io.vertx.core.spi.launcher.CommandFactory;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.fail;


public class ServiceCommandLookupTest {

  ServiceCommandFactoryLoader loader = new ServiceCommandFactoryLoader();

  @Test
  public void testLookup() throws Exception {
    Collection<CommandFactory<?>> commands = loader.lookup();
    ensureCommand(commands, "run");
    ensureCommand(commands, "bare");
    ensureCommand(commands, "version");

    ensureCommand(commands, "list");
    ensureCommand(commands, "start");
    ensureCommand(commands, "stop");
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
}
