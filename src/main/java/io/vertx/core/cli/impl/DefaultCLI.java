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

package io.vertx.core.cli.impl;

import io.vertx.core.cli.ArgumentModel;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.OptionModel;
import io.vertx.core.cli.UsageMessageFormatter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link CLI}.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class DefaultCLI implements CLI {

  protected String name;
  protected String description;
  protected String summary;
  protected boolean hidden;

  protected List<OptionModel> options = new ArrayList<>();
  private Set<ArgumentModel> arguments = new TreeSet<>((o1, o2) -> {
    if (o1.getIndex() == o2.getIndex()) {
      return 1;
    }
    return Integer.valueOf(o1.getIndex()).compareTo(o2.getIndex());
  });

  @Override
  public String getName() {
    return name;
  }

  @Override
  public CLI setName(String name) {
    Objects.requireNonNull(name);
    if (name.isEmpty()) {
      throw new IllegalArgumentException("Invalid command name");
    }
    this.name = name;
    return this;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public CLI setDescription(String desc) {
    Objects.requireNonNull(desc);
    description = desc;
    return this;
  }

  @Override
  public String getSummary() {
    return summary;
  }

  @Override
  public CLI setSummary(String summary) {
    Objects.requireNonNull(summary);
    this.summary = summary;
    return this;
  }

  @Override
  public boolean isHidden() {
    return hidden;
  }

  @Override
  public CLI setHidden(boolean hidden) {
    this.hidden = hidden;
    return this;
  }

  @Override
  public List<OptionModel> getOptions() {
    return options;
  }

  @Override
  public CLI addOption(OptionModel option) {
    Objects.requireNonNull(option);
    options.add(option);
    return this;
  }

  @Override
  public CLI addOptions(List<OptionModel> options) {
    Objects.requireNonNull(options);
    options.forEach(this::addOption);
    return this;
  }

  @Override
  public CLI setOptions(List<OptionModel> options) {
    Objects.requireNonNull(options);
    this.options = new ArrayList<>();
    return addOptions(options);
  }

  @Override
  public List<ArgumentModel> getArguments() {
    return new ArrayList<>(this.arguments);
  }

  @Override
  public CLI addArgument(ArgumentModel arg) {
    Objects.requireNonNull(arg);
    arguments.add(arg);
    return this;
  }

  @Override
  public CLI addArguments(List<ArgumentModel> args) {
    Objects.requireNonNull(args);
    args.forEach(this::addArgument);
    return this;
  }

  @Override
  public CLI setArguments(List<ArgumentModel> args) {
    Objects.requireNonNull(args);
    arguments = new TreeSet<>();
    return addArguments(args);
  }

  @Override
  public OptionModel getOption(String name) {
    Objects.requireNonNull(name);
    // The option by name look up is a three steps lookup:
    // first check by long name
    // then by short name
    // finally by arg name
    for (OptionModel option : options) {
      if (name.equalsIgnoreCase(option.getLongName())) {
        return option;
      }
    }

    for (OptionModel option : options) {
      if (name.equalsIgnoreCase(option.getShortName())) {
        return option;
      }
    }

    for (OptionModel option : options) {
      if (name.equalsIgnoreCase(option.getArgName())) {
        return option;
      }
    }

    return null;
  }

  @Override
  public ArgumentModel getArgument(String name) {
    Objects.requireNonNull(name);
    for (ArgumentModel arg : arguments) {
      if (name.equalsIgnoreCase(arg.getArgName())) {
        return arg;
      }
    }
    return null;
  }

  @Override
  public ArgumentModel getArgument(int index) {
    if (index < 0) {
      throw new IllegalArgumentException("Given index cannot be negative");
    }
    for (ArgumentModel arg : arguments) {
      if (index == arg.getIndex()) {
        return arg;
      }
    }
    return null;
  }


  @Override
  public CLI removeOption(String name) {
    options = options.stream().filter(o -> !o.getLongName().equals(name) && !o.getShortName().equals(name))
        .collect(Collectors.toList());
    return this;
  }

  @Override
  public CLI removeArgument(int index) {
    for (ArgumentModel arg : new TreeSet<>(arguments)) {
      if (arg.getIndex() == index) {
        arguments.remove(arg);
        return this;
      }
    }
    return this;
  }

  @Override
  public CLI usage(StringBuilder builder) {
    new UsageMessageFormatter().usage(builder, this);
    return this;
  }

  @Override
  public CLI usage(StringBuilder builder, String prefix) {
    new UsageMessageFormatter().usage(builder, prefix, this);
    return this;
  }
}
