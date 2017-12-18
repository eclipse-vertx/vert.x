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

package io.vertx.core.cli.converters;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * This 'default' converter tries to create objects using a static 'from' method taking a single String argument.
 * This converter is particularly convenient for converters themselves.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public final class FromStringBasedConverter<T> implements Converter<T> {

  public static final String FROM_STRING = "fromString";
  private final Method method;
  private final Class<T> clazz;

  private FromStringBasedConverter(Class<T> clazz, Method method) {
    this.clazz = clazz;
    this.method = method;
  }

  /**
   * Checks whether the given class can be used by the {@link FromStringBasedConverter} (i.e. has a static
   * 'fromString' method taking a single String as argument). If so, creates a new instance of converter for this
   * type.
   *
   * @param clazz the class
   * @return a {@link FromStringBasedConverter} if the given class is eligible,
   * {@literal null} otherwise.
   */
  public static <T> FromStringBasedConverter<T> getIfEligible(Class<T> clazz) {
    try {
      final Method method = clazz.getMethod(FROM_STRING, String.class);
      if (Modifier.isStatic(method.getModifiers())) {
        if (!method.isAccessible()) {
          method.setAccessible(true);
        }
        return new FromStringBasedConverter<>(clazz, method);
      } else {
        // The from method is present but it must be static.
        return null;
      }
    } catch (NoSuchMethodException e) {
      // The class does not have the right method, return null.
      return null;
    }

  }

  /**
   * Converts the given input to an object by using the 'fromString' method. Notice that the method may
   * receive a {@literal null} value.
   *
   * @param input the input, can be {@literal null}
   * @return the instance of T
   * @throws IllegalArgumentException if the instance of T cannot be created from the input.
   */
  @Override
  public T fromString(String input) throws IllegalArgumentException {
    try {
      return clazz.cast(method.invoke(null, input));
    } catch (IllegalAccessException | InvocationTargetException e) {
      if (e.getCause() != null) {
        throw new IllegalArgumentException(e.getCause());
      } else {
        throw new IllegalArgumentException(e);
      }
    }
  }
}
