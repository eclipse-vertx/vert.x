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

import java.util.*;
import java.util.stream.Collectors;

/**
 * The command line model.
 * <p/>
 * This class is one of the cornerstone of the CLI. It stores the option and argument models and allow retrieving the
 * values.
 */
public class CommandLine {
  private List<String> args = new ArrayList<>();
  private List<OptionModel> options = new ArrayList<>();
  private Set<ArgumentModel> arguments = new TreeSet<>((o1, o2) -> {
    if (o1.getIndex() == o2.getIndex()) {
      return 1;
    }
    return Integer.valueOf(o1.getIndex()).compareTo(o2.getIndex());
  });

  public CommandLine parse(String... cla) throws CommandLineException {
    CommandLineParser parser = new CommandLineParser();
    return parser.parse(this, cla);
  }

  public CommandLine addOption(OptionModel option) {
    options.add(option);
    return this;
  }

  public CommandLine addArgument(ArgumentModel arg) {
    arguments.add(arg);
    return this;
  }

  public void validate() throws CommandLineException {
    // Add value to the specified arguments
    Iterator<ArgumentModel> iterator = arguments.iterator();
    for (String value : args) {
      if (iterator.hasNext()) {
        ArgumentModel model = iterator.next();
        model.process(value);
      }
      // No more defined arguments, just ignore them.
    }

    // Check that all required options and arguments are fulfilled
    for (OptionModel o : options) {
      o.validate();
    }

    List<Integer> usedIndexes = new ArrayList<>();
    for (ArgumentModel a : arguments) {
      a.validate();
      if (usedIndexes.contains(a.getIndex())) {
        throw new CommandLineException("Only one argument can use the index " + a.getIndex());
      }
      usedIndexes.add(a.getIndex());
    }
  }


  protected void argumentFound(String arg) {
    args.add(arg);
  }

  private <T> OptionModel<T> getOption(String name) {
    for (OptionModel option : options) {
      if (option.matches(name)) {
        return option;
      }
    }
    return null;
  }

  private <T> ArgumentModel<T> getArgument(String name) {
    for (ArgumentModel arg : arguments) {
      if (name.equalsIgnoreCase(arg.getArgName())) {
        return arg;
      }
    }
    return null;
  }

  private <T> ArgumentModel<T> getArgument(int index) {
    for (ArgumentModel arg : arguments) {
      if (arg.getIndex() == index) {
        return arg;
      }
    }
    return null;
  }

  public List<OptionModel> getOptions() {
    return options;
  }

  public Set<ArgumentModel> getArguments() {
    return arguments;
  }

  public List<String> getAllArguments() {
    return args;
  }

  public <T> T getOptionValue(String name) {
    final OptionModel<T> option = getOption(name);
    if (option == null) {
      return null;
    } else {
      return option.getValue();
    }
  }

  public <T> T getArgumentValue(String name) {
    final ArgumentModel<T> argument = getArgument(name);
    if (argument == null) {
      return null;
    } else {
      return argument.getValue();
    }
  }

  public <T> T getArgumentValue(int index) {
    final ArgumentModel<T> argument = getArgument(index);
    if (argument == null) {
      return null;
    } else {
      return argument.getValue();
    }
  }


  public <T> List<T> getOptionValues(String name) {
    final OptionModel option = getOption(name);
    if (option == null) {
      return null;
    } else {
      return (List<T>) option.getValues();
    }
  }

  public void addOptions(List<OptionModel> opts) {
    options.addAll(opts);
  }

  public void clear() {
    getOptions().stream().forEach(OptionModel::clear);
    getArguments().stream().forEach(ArgumentModel::clear);
    getAllArguments().clear();
  }

  public CommandLine removeOption(String name) {
    options = getOptions().stream()
        .filter(o -> !name.equalsIgnoreCase(o.getShortName()) && !name.equalsIgnoreCase(o.getLongName()))
        .collect(Collectors.toList());
    return this;
  }

  public boolean hasBeenSet(String name) {
    OptionModel option = getOption(name);
    return option != null && option.hasBeenSet();
  }

  public void addArguments(List<ArgumentModel> args) {
    arguments.addAll(args);
  }
}
