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
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class ReflectionUtils {

  public static <T> T newInstance(Class<T> clazz) {
    try {
      return clazz.newInstance();
    } catch (Exception e) {
      throw new IllegalArgumentException("Cannot instantiate " + clazz.getName(), e);
    }
  }

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
}
