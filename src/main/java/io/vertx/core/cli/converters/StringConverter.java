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

/**
 * Converts String to String, that's the easy one.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public final class StringConverter implements Converter<String> {

  /**
   * The converter.
   */
  public static final StringConverter INSTANCE = new StringConverter();

  private StringConverter() {
    // No direct instantiation
  }

  /**
   * Just returns the given input.
   *
   * @param input the input, can be {@literal null}
   * @return the input
   */
  @Override
  public String fromString(String input) throws IllegalArgumentException {
    return input;
  }
}
