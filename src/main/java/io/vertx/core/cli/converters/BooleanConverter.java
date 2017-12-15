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
