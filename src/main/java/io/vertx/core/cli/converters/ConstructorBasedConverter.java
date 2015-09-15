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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * This 'default' converter tries to create objects using a constructor taking a single String argument.
 * Be aware that implementation must also handle the case where the input is {@literal null}.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public final class ConstructorBasedConverter<T> implements Converter<T> {

  private final Constructor<T> constructor;

  private ConstructorBasedConverter(Constructor<T> constructor) {
    this.constructor = constructor;
  }

  /**
   * Checks whether the given class can be used by the {@link ConstructorBasedConverter} (i.e. has a constructor
   * taking a single String as argument). If so, creates a new instance of converter for this type.
   *
   * @param clazz the class
   * @return a {@link ConstructorBasedConverter} if the given class is eligible,
   * {@literal null} otherwise.
   */
  public static <T> ConstructorBasedConverter<T> getIfEligible(Class<T> clazz) {
    try {
      final Constructor<T> constructor = clazz.getConstructor(String.class);
      if (!constructor.isAccessible()) {
        constructor.setAccessible(true);
      }
      return new ConstructorBasedConverter<>(constructor);
    } catch (NoSuchMethodException e) {
      // The class does not have the right constructor, return null.
      return null;
    }

  }

  /**
   * Converts the given input to an object by using the constructor approach. Notice that the constructor must
   * expect receiving a {@literal null} value.
   *
   * @param input the input, can be {@literal null}
   * @return the instance of T
   * @throws IllegalArgumentException if the instance of T cannot be created from the input.
   */
  @Override
  public T fromString(String input) throws IllegalArgumentException {
    try {
      return constructor.newInstance(input);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      if (e.getCause() != null) {
        throw new IllegalArgumentException(e.getCause());
      } else {
        throw new IllegalArgumentException(e);
      }
    }
  }

}
