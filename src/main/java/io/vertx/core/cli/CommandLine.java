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

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.cli.impl.DefaultCommandLine;

import java.util.List;

/**
 * The parser transforms a CLI (a model) into an {@link CommandLine}. This {@link CommandLine}
 * has stored the argument and option values. Only  instance of parser should create
 * objects of this type.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
@VertxGen
public interface CommandLine {

  /**
   * Creates a command line object from the {@link CLI}. This object is intended to be used by
   * the parser to set the argument and option values.
   *
   * @param cli the CLI definition
   * @return the command line object
   */
  static CommandLine create(CLI cli) {
    return new DefaultCommandLine(cli);
  }

  /**
   * @return the model of this command line object.
   */
  CLI cli();

  /**
   * @return the ordered list of arguments. Arguments are command line arguments not matching an option.
   */
  List<String> allArguments();

  /**
   * Gets the value of an option with the matching name (can be the long name, short name or arg name).
   *
   * @param name the name
   * @param <T>  the expected type
   * @return the value, {@code null} if not set
   */
  <T> T getOptionValue(String name);

  /**
   * Gets the value of an argument with the matching name (arg name).
   *
   * @param name the name
   * @param <T>  the expected type
   * @return the value, {@code null} if not set
   */
  <T> T getArgumentValue(String name);

  /**
   * Gets the value of an argument with the given index.
   *
   * @param index the index
   * @param <T>   the expected type
   * @return the value, {@code null} if not set
   */
  <T> T getArgumentValue(int index);

  /**
   * Gets the values of an option with the matching name (can be the long name, short name or arg name).
   *
   * @param name the name
   * @param <T>  the expected component type
   * @return the values, {@code null} if not set
   * @see #getRawValues(Option)
   */
  @GenIgnore
  <T> List<T> getOptionValues(String name);

  /**
   * Gets the value of an option marked as a flag.
   * <p/>
   * Calling this method an a non-flag option throws an {@link IllegalStateException}.
   *
   * @param name the option name
   * @return {@code true} if the flag has been set in the command line, {@code false} otherwise.
   */
  boolean isFlagEnabled(String name);

  /**
   * Checks whether or not the given option has been assigned in the command line.
   *
   * @param option the option
   * @return {@code true} if the option has received a value, {@link false} otherwise.
   */
  boolean isOptionAssigned(Option option);

  /**
   * Gets the raw values of the given option. Raw values are simple "String", not converted to the option type.
   *
   * @param option the option
   * @return the list of values, empty if none
   */
  List<String> getRawValues(Option option);

  /**
   * Gets the raw value of the given option. Raw values are the values as given in the user command line.
   *
   * @param option the option
   * @return the value, {@code null} if none.
   */
  String getRawValueForOption(Option option);

  /**
   * Checks whether or not the given option accept more values.
   *
   * @param option the option
   * @return {@link true} if the option accepts more values, {@link false} otherwise.
   */
  boolean acceptMoreValues(Option option);

  /**
   * Gets the raw value of the given argument. Raw values are the values as given in the user command line.
   *
   * @param arg the argument
   * @return the value, {@code null} if none.
   */
  String getRawValueForArgument(Argument arg);

  /**
   * Checks whether or not the given argument has been assigned in the command line.
   *
   * @param arg the argument
   * @return {@code true} if the argument has received a value, {@link false} otherwise.
   */
  boolean isArgumentAssigned(Argument arg);

  /**
   * check whether or not the given option has been seen in the user command line.
   *
   * @param option the option
   * @return {@code true} if the user command line has used the option
   */
  boolean isSeenInCommandLine(Option option);

}
