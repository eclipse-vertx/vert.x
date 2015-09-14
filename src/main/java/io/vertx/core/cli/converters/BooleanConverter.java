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

import java.util.Arrays;
import java.util.List;

/**
 * A converter for boolean. This converter considered as 'true' : "true", "on", "1",
 * "yes". All other values are considered as 'false' (as a consequence, 'null' is considered as 'false').
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public final class BooleanConverter implements Converter<Boolean> {

  /**
   * The converter.
   */
  public static final BooleanConverter INSTANCE = new BooleanConverter();
  /**
   * The set of values considered as 'true'.
   */
  private static final List<String> TRUE = Arrays.asList("true", "yes", "on", "1");

  private BooleanConverter() {
    // No direct instantiation
  }

  /**
   * Creates the boolean value from the given String. If the given String does not match one of the 'true' value,
   * {@code false} is returned.
   *
   * @param value the value
   * @return the boolean object
   */
  @Override
  public Boolean fromString(String value) {
    return value != null && TRUE.contains(value.toLowerCase());
  }
}
