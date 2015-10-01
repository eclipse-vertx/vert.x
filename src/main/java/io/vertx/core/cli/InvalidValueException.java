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
