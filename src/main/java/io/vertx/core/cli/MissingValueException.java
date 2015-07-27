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
package io.vertx.core.cli;

public class MissingValueException extends CommandLineException {
  private final OptionModel option;
  private final ArgumentModel argument;

  public MissingValueException(OptionModel option) {
    super("The option " + option.toString() + " requires a value");
    this.argument = null;
    this.option = option;
  }

  public MissingValueException(ArgumentModel argument) {
    super("The argument '"
        + (argument.getArgName() != null ? argument.getArgName():argument.getIndex())
        + "' is required");
    this.option = null;
    this.argument = argument;
  }

  public OptionModel getOption() {
    return option;
  }

  public ArgumentModel getArgument() {
    return argument;
  }
}
