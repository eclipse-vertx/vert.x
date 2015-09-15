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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Entry point to the converter system.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class Converters {

  private static final Map<Class<?>, Class<?>> PRIMITIVE_TO_WRAPPER_TYPE;

  static {
    Map<Class<?>, Class<?>> primToWrap = new HashMap<>(16);

    primToWrap.put(boolean.class, Boolean.class);
    primToWrap.put(byte.class, Byte.class);
    primToWrap.put(char.class, Character.class);
    primToWrap.put(double.class, Double.class);
    primToWrap.put(float.class, Float.class);
    primToWrap.put(int.class, Integer.class);
    primToWrap.put(long.class, Long.class);
    primToWrap.put(short.class, Short.class);
    primToWrap.put(void.class, Void.class);

    PRIMITIVE_TO_WRAPPER_TYPE = Collections.unmodifiableMap(primToWrap);
  }

  public static <T> T create(Class<T> type, String value) {
    if (type.isPrimitive()) {
      type = wrap(type);
    }
    return getConverter(type).fromString(value);
  }

  public static <T> T create(String value, Converter<T> converter) {
    return converter.fromString(value);
  }

  @SuppressWarnings("unchecked")
  private static <T> Class<T> wrap(Class<T> type) {
    Class<T> wrapped = (Class<T>) PRIMITIVE_TO_WRAPPER_TYPE.get(type);
    return (wrapped == null) ? type : wrapped;
  }

  /**
   * Searches a suitable converter to convert String to the given type.
   *
   * @param type the target type
   * @param <T>  the class
   * @return the parameter converter able to creates instances of the target type from String representations.
   * @throws NoSuchElementException if no converter can be found
   */
  @SuppressWarnings("unchecked")
  private static <T> Converter<T> getConverter(Class<T> type) {
    // check for String first
    if (type == String.class) {
      return (Converter<T>) StringConverter.INSTANCE;
    }

    // Boolean has a special case as they support other form of "truth" such as "yes", "on", "1"...
    if (type == Boolean.class) {
      return (Converter<T>) BooleanConverter.INSTANCE;
    }

    // None of them are there, try default converters in the following order:
    // 1. constructor
    // 2. valueOf
    // 3. from
    // 4. fromString
    Converter<T> converter = ConstructorBasedConverter.getIfEligible(type);
    if (converter != null) {
      return converter;
    }
    converter = ValueOfBasedConverter.getIfEligible(type);
    if (converter != null) {
      return converter;
    }
    converter = FromBasedConverter.getIfEligible(type);
    if (converter != null) {
      return converter;
    }
    converter = FromStringBasedConverter.getIfEligible(type);
    if (converter != null) {
      return converter;
    }

    // Unlike other primitive type, characters cannot be created using the 'valueOf' method,
    // so we need a specific converter. As creating characters is quite rare, this must be the last check.
    if (type == Character.class) {
      return (Converter<T>) CharacterConverter.INSTANCE;
    }

    // running out of converters...
    throw new NoSuchElementException("Cannot find a converter able to create instance of " + type.getName());
  }

  public static <T> Converter<T> newInstance(Class<? extends Converter<T>> type) throws IllegalArgumentException {
    try {
      return type.newInstance();
    } catch (InstantiationException | IllegalAccessException e) {
      throw new IllegalArgumentException("Cannot create a new instance of " + type.getName() + " - it requires an " +
          "public constructor without argument", e);
    }
  }


}
