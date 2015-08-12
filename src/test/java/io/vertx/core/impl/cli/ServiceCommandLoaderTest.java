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
package io.vertx.core.impl.cli;

import io.vertx.core.impl.cli.ServiceCommandLoader;
import io.vertx.core.spi.cli.Command;
import org.junit.Test;

import java.util.Collection;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.fail;


public class ServiceCommandLoaderTest {

  ServiceCommandLoader loader = new ServiceCommandLoader();

  @Test
  public void testLookup() throws Exception {
    final Collection<Command> commands = loader.lookup();
    ensureCommand(commands, "Hello");
    ensureCommand(commands, "Bye");

  }

  private void ensureCommand(Collection<Command> commands, String name) {
    for (Command command : commands) {
      if (command.name().equalsIgnoreCase(name)) {
        return;
      }
    }
    fail("Cannot find '" + name + "' in " + commands.stream().map(Command::name).collect(Collectors.toList()));
  }
}