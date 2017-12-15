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

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.cli.annotations.CLIConfigurator;
import io.vertx.core.cli.impl.DefaultCLI;

import java.util.List;

/**
 * Interface defining a command-line interface (in other words a command such as 'run', 'ls'...).
 * This interface is polyglot to ease reuse such as in Vert.x Shell.
 * <p/>
 * A command line interface has a name, and defines a set of options and arguments. Options are key-value pair such
 * as {@code -foo=bar} or {@code -flag}. The supported formats depend on the used parser. Arguments are unlike
 * options raw values. Options are defined using
 * {@link Option}, while argument are defined using {@link Argument}.
 * <p/>
 * Command line interfaces also define a summary and a description. These attributes are used in the usage generation
 * . To disable the help generation, set the {@code hidden} attribute to {@code true}.
 * <p/>
 * Command Line Interface object does not contains "value", it's a model. It must be evaluated by a
 * parser that returns a {@link CommandLine} object containing the argument and option values.
 *
 * @author Clement Escoffier <clement@apache.org>
 * @see Argument
 * @see Option
 */
@VertxGen
public interface CLI {

  /**
   * Creates an instance of {@link CLI} using the default implementation.
   *
   * @param name the name of the CLI (must not be {@code null})
   * @return the created instance of {@link CLI}
   */
  static CLI create(String name) {
    return new DefaultCLI().setName(name);
  }

  /**
   * Creates an instance of {@link CLI} from the given Java class. It instantiates the {@link CLI} object from the
   * annotations used in the class.
   *
   * @param clazz the annotated class
   * @return the created instance of {@link CLI}
   */
  @GenIgnore
  static CLI create(Class<?> clazz) {
    return CLIConfigurator.define(clazz);
  }

  /**
   * Parses the user command line interface and create a new {@link CommandLine} containing extracting values.
   *
   * @param arguments the arguments
   * @return the creates command line
   */
  CommandLine parse(List<String> arguments);

  /**
   * Parses the user command line interface and create a new {@link CommandLine} containing extracting values.
   *
   * @param arguments the arguments
   * @param validate  enable / disable parsing validation
   * @return the creates command line
   */
  CommandLine parse(List<String> arguments, boolean validate);

  /**
   * @return the CLI name.
   */
  String getName();

  /**
   * Sets the name of the CLI.
   *
   * @param name the name
   * @return the current {@link CLI} instance
   */
  @Fluent
  CLI setName(String name);

  /**
   * @return the CLI description.
   */
  @Nullable String getDescription();

  @Fluent
  CLI setDescription(String desc);

  /**
   * @return the CLI summary.
   */
  @Nullable String getSummary();

  /**
   * Sets the summary of the CLI.
   *
   * @param summary the summary
   * @return the current {@link CLI} instance
   */
  @Fluent
  CLI setSummary(String summary);

  /**
   * Checks whether or not the current {@link CLI} instance is hidden.
   *
   * @return {@code true} if the current {@link CLI} is hidden, {@link false} otherwise
   */
  boolean isHidden();

  /**
   * Sets whether or not the current instance of {@link CLI} must be hidden. Hidden CLI are not listed when
   * displaying usages / help messages. In other words, hidden commands are for power user.
   *
   * @param hidden enables or disables the hidden aspect of the CI
   * @return the current {@link CLI} instance
   */
  @Fluent
  CLI setHidden(boolean hidden);

  /**
   * Gets the list of options.
   *
   * @return the list of options, empty if none.
   */
  List<Option> getOptions();

  /**
   * Adds an option.
   *
   * @param option the option, must not be {@code null}.
   * @return the current {@link CLI} instance
   */
  @Fluent
  CLI addOption(Option option);

  /**
   * Adds a set of options. Unlike {@link #setOptions(List)}}, this method does not remove the existing options.
   * The given list is appended to the existing list.
   *
   * @param options the options, must not be {@code null}
   * @return the current {@link CLI} instance
   */
  @Fluent
  CLI addOptions(List<Option> options);

  /**
   * Sets the list of arguments.
   *
   * @param options the list of options, must not be {@code null}
   * @return the current {@link CLI} instance
   */
  @Fluent
  CLI setOptions(List<Option> options);

  /**
   * Gets the list of defined arguments.
   *
   * @return the list of argument, empty if none.
   */
  List<Argument> getArguments();

  /**
   * Adds an argument.
   *
   * @param arg the argument, must not be {@code null}
   * @return the current {@link CLI} instance
   */
  @Fluent
  CLI addArgument(Argument arg);

  /**
   * Adds a set of arguments. Unlike {@link #setArguments(List)}, this method does not remove the existing arguments.
   * The given list is appended to the existing list.
   *
   * @param args the arguments, must not be {@code null}
   * @return the current {@link CLI} instance
   */
  @Fluent
  CLI addArguments(List<Argument> args);

  /**
   * Sets the list of arguments.
   *
   * @param args the list of arguments, must not be {@code null}
   * @return the current {@link CLI} instance
   */
  @Fluent
  CLI setArguments(List<Argument> args);

  /**
   * Gets an {@link Option} based on its name (short name, long name or argument name).
   *
   * @param name the name, must not be {@code null}
   * @return the {@link Option}, {@code null} if not found
   */
  @Nullable
  Option getOption(String name);

  /**
   * Gets an {@link Argument} based on its name (argument name).
   *
   * @param name the name of the argument, must not be {@code null}
   * @return the {@link Argument}, {@code null} if not found.
   */
  @Nullable
  Argument getArgument(String name);

  /**
   * Gets an {@link Argument} based on its index.
   *
   * @param index the index, must be positive or zero.
   * @return the {@link Argument}, {@code null} if not found.
   */
  @Nullable
  Argument getArgument(int index);

  /**
   * Removes an option identified by its name. This method does nothing if the option cannot be found.
   *
   * @param name the option name
   * @return the current {@link CLI} instance
   */
  @Fluent
  CLI removeOption(String name);

  /**
   * Removes an argument identified by its index. This method does nothing if the argument cannot be found.
   *
   * @param index the argument index
   * @return the current {@link CLI} instance
   */
  @Fluent
  CLI removeArgument(int index);

  /**
   * Generates the usage / help of the current {@link CLI}.
   *
   * @param builder the string builder in which the help is going to be printed
   * @return the current {@link CLI} instance
   */
  @GenIgnore
  CLI usage(StringBuilder builder);

  /**
   * Generates the usage / help of the current {@link CLI}.
   *
   * @param builder the string builder in which the help is going to be printed
   * @param prefix  an optional prefix
   * @return the current {@link CLI} instance
   */
  @GenIgnore
  CLI usage(StringBuilder builder, String prefix);
}
