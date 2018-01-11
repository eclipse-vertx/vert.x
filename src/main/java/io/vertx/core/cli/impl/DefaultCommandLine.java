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

package io.vertx.core.cli.impl;

import io.vertx.core.cli.*;
import io.vertx.core.cli.converters.Converters;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of the {@link CommandLine}.
 * This implementation is <strong>not</strong> thread-safe.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class DefaultCommandLine implements CommandLine {

  protected final CLI cli;
  protected List<String> allArgs = new ArrayList<>();
  protected Map<Option, List<String>> optionValues = new HashMap<>();
  protected List<Option> optionsSeenInCommandLine = new ArrayList<>();
  protected Map<Argument, List<String>> argumentValues = new HashMap<>();
  protected boolean valid;

  public DefaultCommandLine(CLI cli) {
    this.cli = cli;
  }

  /**
   * @return the model of this command line object.
   */
  @Override
  public CLI cli() {
    return cli;
  }

  /**
   * @return the ordered list of not recognized arguments. Unrecognized arguments are command line arguments matching
   * neither known options or defined arguments.
   */
  @Override
  public List<String> allArguments() {
    return allArgs;
  }

  /**
   * Adds an argument value.
   *
   * @param argument the argument
   * @return the current instance of {@link DefaultCommandLine}.
   */
  public CommandLine addArgumentValue(String argument) {
    allArgs.add(argument);
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getOptionValue(String name) {
    Option option = cli.getOption(name);
    if (option == null) {
      return null;
    }
    if (option instanceof TypedOption) {
      return getValue((TypedOption<T>) option);
    } else {
      return (T) getRawValueForOption(option);
    }
  }

  @Override
  public boolean isFlagEnabled(String name) {
    Option option = cli.getOption(name);
    if (option == null) {
      throw new IllegalArgumentException("Cannot find the option '" + name + "'");
    }
    if (option.isFlag()) {
      return optionsSeenInCommandLine.contains(option);
    } else {
      throw new IllegalStateException("Cannot retrieve the flag value on a non-flag option (" + name + ")");
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> List<T> getOptionValues(String name) {
    Option option = cli.getOption(name);
    if (option == null) {
      return null;
    }
    if (option instanceof TypedOption) {
      TypedOption<T> typed = (TypedOption<T>) option;
      if (typed.isParsedAsList()) {
        return createFromList(getRawValueForOption(option), typed);
      } else {
        return getRawValuesForOption(option).stream().map(s -> create(s, typed))
            .collect(Collectors.toList());
      }
    } else {
      return (List<T>) getRawValuesForOption(option);
    }
  }

  /**
   * Gets the values of an argument with the matching index.
   *
   * @param index the index
   * @return the values, {@code null} if not set
   * @see #getArgumentValue(int)
   * @see #getRawValueForArgument(Argument)
   */
  @Override
  @SuppressWarnings("unchecked")
  public <T> List<T> getArgumentValues(int index) {
    Argument argument = cli.getArgument(index);
    if (argument == null) {
      return null;
    }
    if (argument instanceof TypedArgument) {
      TypedArgument<T> typed = (TypedArgument<T>) argument;
      return getRawValuesForArgument(typed).stream().map(s -> create(s, typed))
          .collect(Collectors.toList());
    } else {
      return (List<T>) getRawValuesForArgument(argument);
    }
  }

  @Override
  public <T> T getArgumentValue(String name) {
    Argument arg = cli.getArgument(name);
    if (arg == null) {
      return null;
    }
    return getArgumentValue(arg.getIndex());
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getArgumentValue(int index) {
    Argument arg = cli.getArgument(index);
    if (arg == null) {
      return null;
    }
    if (arg instanceof TypedArgument) {
      return create(getRawValueForArgument(arg), (TypedArgument<T>) arg);
    } else {
      return (T) getRawValueForArgument(arg);
    }
  }

  @Override
  public boolean isOptionAssigned(Option option) {
    return !getRawValuesForOption(option).isEmpty();
  }

  @Override
  public List<String> getRawValuesForOption(Option option) {
    List<?> list = optionValues.get(option);
    if (list != null) {
      return list.stream().map(Object::toString).collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  @Override
  public List<String> getRawValuesForArgument(Argument argument) {
    List<?> list = argumentValues.get(argument);
    if (list != null) {
      return list.stream().map(Object::toString).collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  public DefaultCommandLine addRawValue(Option option, String value) {
    if (!acceptMoreValues(option) && !option.isFlag()) {
      throw new CLIException("The option " + option.getName() + " does not accept value or has " +
          "already been set");
    }
    if (! option.getChoices().isEmpty()  && ! option.getChoices().contains(value)) {
      throw new InvalidValueException(option, value);
    }
    List<String> list = optionValues.get(option);
    if (list == null) {
      list = new ArrayList<>();
      optionValues.put(option, list);
    }
    list.add(value);
    return this;
  }

  @Override
  public String getRawValueForOption(Option option) {
    if (isOptionAssigned(option)) {
      return getRawValuesForOption(option).get(0);
    }
    return option.getDefaultValue();
  }

  @Override
  public boolean acceptMoreValues(Option option) {
    return option.isMultiValued() || option.isSingleValued() && !isOptionAssigned(option);
  }

  @Override
  public String getRawValueForArgument(Argument arg) {
    List values = argumentValues.get(arg);
    if (values == null || values.isEmpty()) {
      return arg.getDefaultValue();
    }
    return values.get(0).toString();
  }

  public DefaultCommandLine setRawValue(Argument arg, String rawValue) {
    List<String> list = argumentValues.get(arg);
    if (list == null) {
      list = new ArrayList<>();
      argumentValues.put(arg, list);
    }
    list.add(rawValue);
    return this;
  }

  @Override
  public boolean isArgumentAssigned(Argument arg) {
    return argumentValues.get(arg) != null;
  }

  /**
   * Marks the option as seen.
   *
   * @param option the option
   * @return the current instance of {@link DefaultCommandLine}.
   */
  public DefaultCommandLine setSeenInCommandLine(Option option) {
    optionsSeenInCommandLine.add(option);
    return this;
  }

  @Override
  public boolean isSeenInCommandLine(Option option) {
    return optionsSeenInCommandLine.contains(option);
  }


  private <T> T getValue(TypedOption<T> option) {
    if (isOptionAssigned(option)) {
      return create(getRawValueForOption(option), option);
    } else {
      if (option.getDefaultValue() != null) {
        return create(getRawValueForOption(option), option);
      }
      if (option.isFlag() || isBoolean(option)) {
        try {
          if (isSeenInCommandLine(option)) {
            return (T) Boolean.TRUE;
          } else {
            return (T) Boolean.FALSE;
          }
        } catch (InvalidValueException e) {
          throw new IllegalArgumentException(e);
        }
      }
    }
    return null;
  }

  private boolean isBoolean(TypedOption option) {
    Class type = option.getType();
    return type == Boolean.TYPE || type == Boolean.class;
  }

  /**
   * Creates the value for the given argument from the given raw value.
   *
   * @param value the value
   * @return the created value
   */
  public static <T> T create(String value, TypedArgument<T> argument) {
    Objects.requireNonNull(argument);
    if (value == null) {
      value = argument.getDefaultValue();
    }

    if (value == null) {
      return null;
    }

    try {
      if (argument.getConverter() != null) {
        return Converters.create(value, argument.getConverter());
      } else {
        return Converters.create(argument.getType(), value);
      }
    } catch (Exception e) {
      throw new InvalidValueException(argument, value, e);
    }
  }

  /**
   * Creates the value for the given option from the given raw value.
   *
   * @param value the value
   * @return the created value
   */
  public static <T> T create(String value, TypedOption<T> option) {
    Objects.requireNonNull(option);
    if (value == null) {
      value = option.getDefaultValue();
    }

    if (value == null) {
      return null;
    }

    try {
      if (option.getConverter() != null) {
        return Converters.create(value, option.getConverter());
      } else {
        return Converters.create(option.getType(), value);
      }
    } catch (Exception e) {
      throw new InvalidValueException(option, value, e);
    }
  }


  public static <T> List<T> createFromList(String raw, TypedOption<T> option) {
    if (raw == null) {
      return Collections.emptyList();
    }
    final String[] segments = raw.split(option.getListSeparator());
    return Arrays.stream(segments).map(s -> create(s.trim(), option)).collect(Collectors.toList());
  }

  /**
   * Checks whether or not the command line is valid, i.e. all constraints from arguments and options have been
   * satisfied. This method is used when the parser validation is disabled.
   *
   * @return {@code true} if the current {@link CommandLine} object is valid. {@link false} otherwise.
   */
  @Override
  public boolean isValid() {
    return valid;
  }

  /**
   * Sets whether or not the {@link CommandLine} is valid.
   *
   * @param validity {@code true} if the command line is valid.
   */
  void setValidity(boolean validity) {
    this.valid = validity;
  }

  /**
   * Checks whether or not the user has passed a "help" option and is asking for help.
   *
   * @return {@code true} if the user command line has enabled a "Help" option, {@link false} otherwise.
   */
  @Override
  public boolean isAskingForHelp() {
    for (Option option : cli.getOptions()) {
      if (option.isHelp() && isSeenInCommandLine(option)) {
        return true;
      }
    }
    return false;
  }
}
