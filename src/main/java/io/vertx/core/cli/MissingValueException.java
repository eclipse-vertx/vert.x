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

package io.vertx.core.cli;

/**
 * Exception thrown when an option requiring a value does not receive the value, or when a mandatory argument does not
 * receive a value.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class MissingValueException extends CLIException {
  private final Option option;
  private final Argument argument;

  /**
   * Creates a new instance of {@link MissingValueException} when an option requiring a value does not receive a value.
   *
   * @param option the option
   */
  public MissingValueException(Option option) {
    super("The option '" + option.getName() + "' requires a value");
    this.argument = null;
    this.option = option;
  }

  /**
   * Creates a new instance of {@link MissingValueException} when a mandatory argument is not set in the user command
   * line.
   *
   * @param argument the argument
   */
  public MissingValueException(Argument argument) {
    super("The argument '"
        + (argument.getArgName() != null ? argument.getArgName() : argument.getIndex())
        + "' is required");
    this.option = null;
    this.argument = argument;
  }

  /**
   * @return the option, may be {@code null} if the exception is about an argument.
   */
  public Option getOption() {
    return option;
  }

  /**
   * @return the argument, may be {@code null} if the exception is about an option.
   */
  public Argument getArgument() {
    return argument;
  }
}
