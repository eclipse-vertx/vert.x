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
package io.vertx.core.spi.cli;

public class InvalidValueException extends CommandLineException {
  private final OptionModel option;
  private final ArgumentModel argument;
  private final String value;

  public InvalidValueException(OptionModel option, String value) {
    this(option, value, null);
  }

  public InvalidValueException(ArgumentModel argument, String value) {
    this(argument, value, null);
  }

  public InvalidValueException(ArgumentModel argument, String value, Exception cause) {
    super("The value '" + value + "' is not accepted by the argument '"
        + (argument.getArgName() != null ? argument.getArgName():argument.getIndex()) + "'", cause);
    this.option = null;
    this.value = value;
    this.argument = argument;
  }

  public <T> InvalidValueException(OptionModel option, String value, Exception cause) {
    super("The value '" + value + "' is not accepted by " + option, cause);
    this.argument = null;
    this.value = value;
    this.option = option;
  }

  public OptionModel getOption() {
    return option;
  }

  public String getValue() {
    return value;
  }

  public ArgumentModel getArgument() {
    return argument;
  }
}
