/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 *     The Apache License v2.0 is available at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.cli.converters;


/**
 * A converter for character. Unlike other primitive types, characters cannot be created using 'valueOf'. Notice that
 * only input having a length of 1 can be converted to characters. Other inputs are rejected.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public final class CharacterConverter implements Converter<Character> {

  /**
   * The converter.
   */
  public static final CharacterConverter INSTANCE = new CharacterConverter();

  private CharacterConverter() {
    // No direct instantiation
  }

  @Override
  public Character fromString(String input) throws IllegalArgumentException {
    if (input == null) {
      throw new NullPointerException("input must not be null");
    }

    if (input.length() != 1) {
      throw new IllegalArgumentException("The input string \"" + input + "\" cannot be converted to a " +
          "character. The input's length must be 1");
    }

    return input.toCharArray()[0];

  }
}
