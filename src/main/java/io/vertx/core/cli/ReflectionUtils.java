package io.vertx.core.cli;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Some utilities methods to ease reflection calls.
 */
public class ReflectionUtils {

  public static boolean isSetter(Method method) {
    return method.getName().startsWith("set") && method.getParameterTypes().length == 1;
  }

  public static List<Method> getSetterMethods(Class<?> clazz) {
    return Arrays.stream(clazz.getMethods()).filter(ReflectionUtils::isSetter).collect(Collectors.toList());
  }

  public static boolean isMultiple(Method setter) {
    final Class<?> type = setter.getParameterTypes()[0];
    return type.isArray() || List.class.isAssignableFrom(type) || Set.class.isAssignableFrom(type)
        || Collection.class.isAssignableFrom(type);
  }

  public static Class getComponentType(Parameter parameter) {
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

  public static String getJar() {
    // Check whether or not the "sun.java.command" system property is defined,
    // if it is, check whether the first segment of the command ends with ".jar".
    final String command = getCommand();
    if (command != null) {
      final String[] segments = command.split(" ");
      if (segments.length >= 1) {
        // Fat Jar ?
        if (segments[0].endsWith(".jar")) {
          return segments[0];
        }
      }
    } else {
      // Second attend is to check the classpath. If the classpath contains only one element, it's the fat jar
      String classpath = System.getProperty("java.class.path");
      if (!classpath.isEmpty() && !classpath.contains(File.pathSeparator) && classpath.endsWith(".jar")) {
        return classpath;
      }
    }

    return null;

  }

  public static String getCommand() {
    return System.getProperty("sun.java.command");
  }

  public static String getFirstSegmentOfCommand() {
    String cmd = getCommand();
    if (cmd != null) {
      final String[] segments = cmd.split(" ");
      if (segments.length >= 1) {
        return segments[0];
      }
    }
    return null;
  }
}
