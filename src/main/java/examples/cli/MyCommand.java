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

package examples.cli;

// tag::content[]

import io.vertx.core.cli.CLIException;
import io.vertx.core.cli.annotations.Name;
import io.vertx.core.cli.annotations.Option;
import io.vertx.core.cli.annotations.Summary;
import io.vertx.core.spi.launcher.DefaultCommand;

@Name("my-command")
@Summary("A simple hello command.")
public class MyCommand extends DefaultCommand {

  private String name;

  @Option(longName = "name", required = true)
  public void setName(String n) {
    this.name = n;
  }

  @Override
  public void run() throws CLIException {
    System.out.println("Hello " + name);
  }
}

// end::content[]
