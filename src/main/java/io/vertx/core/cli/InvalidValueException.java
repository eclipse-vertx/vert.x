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
 * Exception thrown when an option or an argument receives an invalid value.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class InvalidValueException extends CLIException {
  private final Option option;
  private final Argument argument;
  private final String value;

  /**
   * Creates a new instance of {@link InvalidValueException} for the given option and the given value. This
   * constructor is used when the option receives a value while it does not accept another value.
   *
   * @param option the option
   * @param value  the value
   */
  public InvalidValueException(Option option, String value) {
    this(option, value, null);
  }


  /**
   * Creates a new instance of {@link InvalidValueException} for the given argument and the given value. This
   * constructor is used when the argument receives a value that cannot be "converted" to the desired type.
   *
   * @param argument the argument
   * @param value    the value
   * @param cause    the cause
   */
  public InvalidValueException(Argument argument, String value, Exception cause) {
    super("The value '" + value + "' is not accepted by the argument '"
        + (argument.getArgName() != null ? argument.getArgName() : argument.getIndex()) + "'", cause);
    this.option = null;
    this.value = value;
    this.argument = argument;
  }

  /**
   * Creates a new instance of {@link InvalidValueException} for the given option and the given value. This
   * constructor is used when the options receives a value that cannot be "converted" to the desired type.
   *
   * @param option the option
   * @param value  the value
   * @param cause  the cause
   */
  public InvalidValueException(Option option, String value, Exception cause) {
    super("The value '" + value + "' is not accepted by '" + option.getName() + "'", cause);
    this.argument = null;
    this.value = value;
    this.option = option;
  }

  /**
   * @return the option, may be {@code null} if the exception is about an argument.
   */
  public Option getOption() {
    return option;
  }

  /**
   * @return the invalid value.
   */
  public String getValue() {
    return value;
  }

  /**
   * @return the argument, may be {@code null} if the exception is about an option.
   */
  public Argument getArgument() {
    return argument;
  }
}
