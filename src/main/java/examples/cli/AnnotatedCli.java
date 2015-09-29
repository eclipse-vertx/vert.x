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
