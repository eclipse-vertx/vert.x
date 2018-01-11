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
