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
package io.vertx.core.cli.converters;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * This 'default' converter tries to create objects using a static 'valueOf' method taking a single String argument.
 * This converter is particularly convenient to for enumeration and primitive types.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public final class ValueOfBasedConverter<T> implements Converter<T> {

  public static final String VALUE_OF = "valueOf";
  private final Method method;
  private final Class<T> clazz;

  private ValueOfBasedConverter(Class<T> clazz, Method method) {
    this.clazz = clazz;
    this.method = method;
  }

  /**
   * Checks whether the given class can be used by the {@link ValueOfBasedConverter} (i.e. has a static 'valueOf'
   * method taking a single String as argument). If so, creates a new instance of converter for this type.
   *
   * @param clazz the class
   * @return a {@link ValueOfBasedConverter} if the given class is eligible,
   * {@literal null} otherwise.
   */
  @SuppressWarnings("unchecked")
  public static <T> ValueOfBasedConverter<T> getIfEligible(Class<T> clazz) {
    try {
      final Method method = clazz.getMethod(VALUE_OF, String.class);
      if (Modifier.isStatic(method.getModifiers())) {
        if (!method.isAccessible()) {
          method.setAccessible(true);
        }
        return new ValueOfBasedConverter(clazz, method);
      } else {
        // The valueOf method is present but it must be static.
        return null;
      }
    } catch (NoSuchMethodException e) {
      // The class does not have the right method, return null.
      return null;
    }

  }

  /**
   * Converts the given input to an object by using the 'valueOf' method. Notice that the method may
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
