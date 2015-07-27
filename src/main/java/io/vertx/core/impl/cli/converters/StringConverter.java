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
package io.vertx.core.impl.cli.converters;

import io.vertx.core.spi.cli.Converter;

/**
 * Converts String to String, that's the easy one.
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
