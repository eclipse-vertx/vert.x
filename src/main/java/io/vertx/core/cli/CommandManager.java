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
    commandLine.addOptions(command.options());
    commandLine.addArguments(command.arguments());
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

  private static <A extends Annotation> Handler<A> getHandler(Class<A> type) {
    return (Handler<A>) HANDLERS.get(type);
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

  private interface Handler<A extends Annotation> {

    void inject(A annotation, Command command, Method method, CommandLine commandLine) throws CommandLineException;

  }

  private static class OptionHandler implements Handler<Option> {

    @Override
    public void inject(Option option, Command command, Method method, CommandLine commandLine) throws CommandLineException {
      String name = option.longName();
      if (name == null) {
        name = option.shortName();
      }

      try {
        if (ReflectionUtils.isMultiple(method)) {
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
  }


}
