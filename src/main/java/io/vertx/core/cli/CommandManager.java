package io.vertx.core.cli;


import io.vertx.core.spi.Command;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.util.*;

public class CommandManager {

  private static Map<Class<? extends Annotation>, Handler<?>> HANDLERS = new HashMap<>();

  static {
    HANDLERS.put(Option.class, new OptionHandler());
    HANDLERS.put(Argument.class, new ArgumentHandler());
  }


  public static Command define(Command command, CommandLine commandLine) {
    final Method[] methods = command.getClass().getMethods();
    for (Method method : methods) {
      if (method.getName().startsWith("set") && method.getParameterTypes().length == 1) {
        for (Annotation annotation : method.getDeclaredAnnotations()) {
          final Handler handler = getHandler(annotation.annotationType());
          if (handler != null) {
            handler.define(annotation, command, method, commandLine);
          }
        }
      }
    }
    return command;
  }

  public static Command inject(Command command, CommandLine commandLine) throws CommandLineException {
    final Method[] methods = command.getClass().getMethods();
    for (Method method : methods) {
      if (method.getName().startsWith("set") && method.getParameterTypes().length == 1) {
        // Valid setter, find binding.
        for (Annotation annotation : method.getDeclaredAnnotations()) {
          final Handler handler = getHandler(annotation.annotationType());
          if (handler != null) {
            handler.inject(annotation, command, method, commandLine);
          }
        }
      }

    }
    return command;
  }

  public static boolean isHidden(Command command) {
    return command.getClass().getAnnotation(Hidden.class) != null;
  }

  public static String getSummary(Command command) {
    final Summary summary = command.getClass().getAnnotation(Summary.class);
    if (summary != null) {
      return summary.value();
    }
    return "no summary";
  }

  private static <A extends Annotation> Handler<A> getHandler(Class<A> type) {
    return (Handler<A>) HANDLERS.get(type);
  }

  private static Class extractContainedType(Parameter parameter) {
    Class<?> type = parameter.getType();
    if (type.isArray()) {
      return type.getComponentType();
    }

    if (parameter.getParameterizedType() != null) {
      return (Class) ((ParameterizedType) parameter.getParameterizedType()).getActualTypeArguments()[0];
    }

    if (parameter.getType().getGenericSuperclass() instanceof ParameterizedType) {
      return (Class) ((ParameterizedType) parameter.getType().getGenericSuperclass()).getActualTypeArguments()[0];
    }

    return null;
  }

  private static boolean isMultiple(Method setter) {
    final Class<?> type = setter.getParameterTypes()[0];
    return type.isArray() || List.class.isAssignableFrom(type) || Set.class.isAssignableFrom(type)
        || Collection.class.isAssignableFrom(type);
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

  public static String getDescription(Command command) {
    final Description description = command.getClass().getAnnotation(Description.class);
    if (description != null) {
      return description.value();
    }
    return "";
  }

  private interface Handler<A extends Annotation> {

    void inject(A annotation, Command command, Method method, CommandLine commandLine) throws CommandLineException;

    void define(A annotation, Command command, Method method, CommandLine commandLine);
  }

  private static class OptionHandler implements Handler<Option> {

    @Override
    public void inject(Option option, Command command, Method method, CommandLine commandLine) throws CommandLineException {
      String name = option.longName();
      if (name == null) {
        name = option.shortName();
      }

      try {
        if (isMultiple(method)) {
          Object toBeInjected = createMultiValueContainer(method, commandLine.getOptionValues(name));
          if (toBeInjected != null) {
            method.invoke(command, toBeInjected);
          }
        } else {
          final Object value = commandLine.getOptionValue(name);
          if (value != null) {
            method.invoke(command, value);
          }
        }
      } catch (Exception e) {
        throw new CommandLineException("Cannot inject value for option '" + name + "' (" + commandLine.getOptionValues(name) + ")", e);
      }
    }

    @Override
    public void define(Option annotation, Command command, Method method, CommandLine commandLine) {
      final OptionModel.Builder<Object> builder = OptionModel.builder()
          .longName(annotation.longName())
          .shortName(annotation.shortName())
          .argName(annotation.name())
          .isRequired(annotation.required())
          .acceptValue(annotation.acceptValue());

      // Get type.
      if (isMultiple(method)) {
        builder.acceptMultipleValues();
        builder.type(extractContainedType(method.getParameters()[0]));
      } else {
        builder.type((Class) method.getParameterTypes()[0]);
      }

      // Companion annotations
      Description description = method.getAnnotation(Description.class);
      if (description != null) {
        builder.description(description.value());
      }

      ParsedAsList parsedAsList = method.getAnnotation(ParsedAsList.class);
      if (parsedAsList != null) {
        builder.listSeparator(parsedAsList.separator());
      }

      Hidden hidden = method.getAnnotation(Hidden.class);
      if (hidden != null) {
        builder.hidden();
      }

      ConvertedBy convertedBy = method.getAnnotation(ConvertedBy.class);
      if (convertedBy != null) {
        builder.convertedBy((Class) convertedBy.value());
      }

      DefaultValue defaultValue = method.getAnnotation(DefaultValue.class);
      if (defaultValue != null) {
        builder.defaultValue(defaultValue.value());
      }

      commandLine.addOption(builder.build());
    }
  }

  private static class ArgumentHandler implements Handler<Argument> {

    @Override
    public void inject(Argument argument, Command command, Method method, CommandLine commandLine) throws
        CommandLineException {
      try {
        final Object value = commandLine.getArgumentValue(argument.index());
        if (value != null) {
          method.invoke(command, value);
        }
      } catch (Exception e) {
        throw new CommandLineException("Cannot inject value for argument #" + argument.index(), e);
      }
    }

    @Override
    public void define(Argument annotation, Command command, Method method, CommandLine commandLine) {
      final ArgumentModel.Builder<Object> builder = ArgumentModel.builder()
          .index(annotation.index())
          .argName(annotation.name())
          .required(annotation.required());

      builder.type((Class) method.getParameterTypes()[0]);

      // Companion annotations
      Description description = method.getAnnotation(Description.class);
      if (description != null) {
        builder.description(description.value());
      }

      Hidden hidden = method.getAnnotation(Hidden.class);
      if (hidden != null) {
        builder.hidden();
      }

      ConvertedBy convertedBy = method.getAnnotation(ConvertedBy.class);
      if (convertedBy != null) {
        builder.convertedBy((Class) convertedBy.value());
      }

      DefaultValue defaultValue = method.getAnnotation(DefaultValue.class);
      if (defaultValue != null) {
        builder.defaultValue(defaultValue.value());
      }

      commandLine.addArgument(builder.build());
    }
  }


}
