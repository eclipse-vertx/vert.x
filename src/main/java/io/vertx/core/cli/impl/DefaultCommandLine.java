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
  protected Map<Argument, Object> argumentValues = new HashMap<>();

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
        return getRawValues(option).stream().map(s -> create(s, typed))
            .collect(Collectors.toList());
      }
    } else {
      return (List<T>) getRawValues(option);
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
    return !getRawValues(option).isEmpty();
  }

  @Override
  public List<String> getRawValues(Option option) {
    List<?> list = optionValues.get(option);
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
      return getRawValues(option).get(0);
    }
    return option.getDefaultValue();
  }

  @Override
  public boolean acceptMoreValues(Option option) {
    return option.isMultiValued() || option.isSingleValued() && !isOptionAssigned(option);
  }

  @Override
  public String getRawValueForArgument(Argument arg) {
    Object v = argumentValues.get(arg);
    if (v == null) {
      return arg.getDefaultValue();
    }
    return v.toString();
  }

  public DefaultCommandLine setRawValue(Argument arg, String rawValue) {
    argumentValues.put(arg, rawValue);
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
}
