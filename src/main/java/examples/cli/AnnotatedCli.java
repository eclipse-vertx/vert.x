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

import io.vertx.core.cli.annotations.*;

/**
 * An example of annotated cli.
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
// tag::content[]
@Name("some-name")
@Summary("some short summary.")
@Description("some long description")
public class AnnotatedCli {

  private boolean flag;
  private String name;
  private String arg;

  @Option(shortName = "f", flag = true)
  public void setFlag(boolean flag) {
    this.flag = flag;
  }

  @Option(longName = "name")
  public void setName(String name) {
    this.name = name;
  }

  @Argument(index = 0)
  public void setArg(String arg) {
    this.arg = arg;
  }
}
// end::content[]
