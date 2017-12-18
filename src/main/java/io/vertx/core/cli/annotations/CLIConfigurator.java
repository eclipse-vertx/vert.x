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

package io.vertx.core.cli.annotations;

import io.vertx.core.cli.*;
import io.vertx.core.cli.impl.DefaultCLI;
import io.vertx.core.cli.impl.ReflectionUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Class responsible for defining CLI using annotations and injecting values extracted by the parser.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class CLIConfigurator {


  /**
   * Creates an instance of the given class, and extracts the metadata from the given class.
   *
   * @param clazz the CLI class
   * @return the defined CLI.
   */
  public static CLI define(Class<?> clazz) {
    CLI cli = new DefaultCLI();

    // Class annotations
    final Summary summary = clazz.getAnnotation(Summary.class);
    final Description desc = clazz.getAnnotation(Description.class);
    final Hidden hidden = clazz.getAnnotation(Hidden.class);
    final Name name = clazz.getAnnotation(Name.class);

    if (name == null) {
      throw new IllegalArgumentException("The command cannot be defined, the @Name annotation is missing.");
    }
    if (name.value() == null || name.value().isEmpty()) {
      throw new IllegalArgumentException("The command cannot be defined, the @Name value is empty or null.");
    }
    cli.setName(name.value());

    if (summary != null) {
      cli.setSummary(summary.value());
    }
    if (desc != null) {
      cli.setDescription(desc.value());
    }
    if (hidden != null) {
      cli.setHidden(true);
    }

    // Setter annotations
    final List<Method> methods = ReflectionUtils.getSetterMethods(clazz);
    for (Method method : methods) {
      final Option option = method.getAnnotation(Option.class);
      final Argument argument = method.getAnnotation(Argument.class);

      if (option != null) {
        cli.addOption(createOption(method));
      }
      if (argument != null) {
        cli.addArgument(createArgument(method));
      }
    }

    return cli;
  }

  @SuppressWarnings("unchecked")
  private static io.vertx.core.cli.Option createOption(Method method) {
    TypedOption opt = new TypedOption();

    // Option
    Option option = method.getAnnotation(Option.class);
    opt.setLongName(option.longName())
        .setShortName(option.shortName())
        .setMultiValued(option.acceptMultipleValues())
        .setSingleValued(option.acceptValue())
        .setArgName(option.argName())
        .setFlag(option.flag())
        .setHelp(option.help())
        .setRequired(option.required());

    // Description
    Description description = method.getAnnotation(Description.class);
    if (description != null) {
      opt.setDescription(description.value());
    }

    Hidden hidden = method.getAnnotation(Hidden.class);
    if (hidden != null) {
      opt.setHidden(true);
    }

    if (ReflectionUtils.isMultiple(method)) {
      opt
          .setType(ReflectionUtils.getComponentType(method.getParameters()[0]))
          .setMultiValued(true);
    } else {
      final Class<?> type = method.getParameters()[0].getType();
      opt.setType(type);
      if (type != Boolean.TYPE && type != Boolean.class) {
        // In the case of a boolean, it may be a flag, need explicit settings.
        opt.setSingleValued(true);
      }
    }

    ConvertedBy convertedBy = method.getAnnotation(ConvertedBy.class);
    if (convertedBy != null) {
      opt.setConverter(ReflectionUtils.newInstance(convertedBy.value()));
    }
    ParsedAsList parsedAsList = method.getAnnotation(ParsedAsList.class);
    if (parsedAsList != null) {
      opt.setParsedAsList(true).setListSeparator(parsedAsList.separator());
    }

    // Default value
    DefaultValue defaultValue = method.getAnnotation(DefaultValue.class);
    if (defaultValue != null) {
      opt.setDefaultValue(defaultValue.value());
    }

    opt.ensureValidity();

    return opt;
  }

  @SuppressWarnings("unchecked")
  private static io.vertx.core.cli.Argument createArgument(Method method) {
    TypedArgument arg = new TypedArgument();

    // Argument
    Argument argument = method.getAnnotation(Argument.class);
    arg.setIndex(argument.index());
    arg.setArgName(argument.argName());
    arg.setRequired(argument.required());

    // Description
    Description description = method.getAnnotation(Description.class);
    if (description != null) {
      arg.setDescription(description.value());
    }

    if (ReflectionUtils.isMultiple(method)) {
      arg
          .setType(ReflectionUtils.getComponentType(method.getParameters()[0]))
          .setMultiValued(true);
    } else {
      final Class<?> type = method.getParameters()[0].getType();
      arg.setType(type);
    }

    Hidden hidden = method.getAnnotation(Hidden.class);
    if (hidden != null) {
      arg.setHidden(true);
    }

    ConvertedBy convertedBy = method.getAnnotation(ConvertedBy.class);
    if (convertedBy != null) {
      arg.setConverter(ReflectionUtils.newInstance(convertedBy.value()));
    }

    // Default value
    DefaultValue defaultValue = method.getAnnotation(DefaultValue.class);
    if (defaultValue != null) {
      arg.setDefaultValue(defaultValue.value());
    }

    return arg;
  }

  private static Object getOptionValue(Method method, String name, CommandLine commandLine) {
    final io.vertx.core.cli.Option option = commandLine.cli().getOption(name);
    if (option == null) {
      return null;
    }
    boolean multiple = ReflectionUtils.isMultiple(method);
    if (multiple) {
      return createMultiValueContainer(method, commandLine.getOptionValues(name));
    }
    return commandLine.getOptionValue(name);
  }

  private static Object getArgumentValue(Method method, int index, CommandLine commandLine) {
    final io.vertx.core.cli.Argument argument = commandLine.cli().getArgument(index);
    if (argument == null) {
      return null;
    }

    boolean multiple = ReflectionUtils.isMultiple(method);
    if (multiple) {
      return createMultiValueContainer(method, commandLine.getArgumentValues(argument.getIndex()));
    }
    return commandLine.getArgumentValue(argument.getIndex());
  }

  /**
   * Injects the value in the annotated setter methods ({@link Option} and {@link Argument}.
   *
   * @param cli    the cli
   * @param object the object to be injected
   * @throws CLIException if an injection issue occurred.
   */
  public static void inject(CommandLine cli, Object object) throws CLIException {
    final List<Method> methods = ReflectionUtils.getSetterMethods(object.getClass());
    for (Method method : methods) {
      Option option = method.getAnnotation(Option.class);
      Argument argument = method.getAnnotation(Argument.class);
      if (option != null) {
        String name = option.longName();
        if (name == null) {
          name = option.shortName();
        }
        try {
          Object injected = getOptionValue(method, name, cli);
          if (injected != null) {
            method.setAccessible(true);
            method.invoke(object, injected);
          }
        } catch (Exception e) {
          throw new CLIException("Cannot inject value for option '" + name + "'", e);
        }
      }

      if (argument != null) {
        int index = argument.index();
        try {
          Object injected = getArgumentValue(method, index, cli);
          if (injected != null) {
            method.setAccessible(true);
            method.invoke(object, injected);
          }
        } catch (Exception e) {
          throw new CLIException("Cannot inject value for argument '" + index + "'", e);
        }
      }

    }
  }

  private static <T> Object createMultiValueContainer(Method setter, List<T> values) {
    final Class<?> type = setter.getParameterTypes()[0];
    if (type.isArray()) {
      Object array = Array.newInstance(type.getComponentType(), values.size());
      for (int i = 0; i < values.size(); i++) {
        Array.set(array, i, values.get(i));
      }
      return array;
    }

    if (Set.class.isAssignableFrom(type)) {
      return new LinkedHashSet<>(values);
    }

    if (List.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type)) {
      return values;
    }

    return null;
  }

}
