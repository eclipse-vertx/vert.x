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
  private static final Map<Class<?>, Converter<?>> WELL_KNOWN_CONVERTERS;

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

    Map<Class<?>, Converter<?>> wellKnown = new HashMap<>(16);
    wellKnown.put(Boolean.class, BooleanConverter.INSTANCE);
    wellKnown.put(Byte.class, Byte::parseByte);
    wellKnown.put(Character.class, CharacterConverter.INSTANCE);
    wellKnown.put(Double.class, Double::parseDouble);
    wellKnown.put(Float.class, Float::parseFloat);
    wellKnown.put(Integer.class, Integer::parseInt);
    wellKnown.put(Long.class, Long::parseLong);
    wellKnown.put(Short.class, Short::parseShort);
    wellKnown.put(String.class, value -> value);

    WELL_KNOWN_CONVERTERS = Collections.unmodifiableMap(wellKnown);
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
    // check for well known types first
    if (WELL_KNOWN_CONVERTERS.containsKey(type)) {
      return (Converter<T>) WELL_KNOWN_CONVERTERS.get(type);
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

    // running out of converters...
    throw new NoSuchElementException("Cannot find a converter able to create instance of " + type.getName());
  }

  public static <T> Converter<T> newInstance(Class<? extends Converter<T>> type) throws IllegalArgumentException {
    try {
      return type.getDeclaredConstructor().newInstance();
    } catch (InvocationTargetException | NoSuchMethodException | InstantiationException | IllegalAccessException e) {
      throw new IllegalArgumentException("Cannot create a new instance of " + type.getName() + " - it requires an " +
          "public constructor without argument", e);
    }
  }


}
