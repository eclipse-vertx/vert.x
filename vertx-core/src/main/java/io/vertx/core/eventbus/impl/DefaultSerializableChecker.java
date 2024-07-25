/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.eventbus.impl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

public enum DefaultSerializableChecker {

  INSTANCE;

  private final Set<String> classNames;

  DefaultSerializableChecker() {
    classNames = Stream.of(
      byte[].class,
      Number.class,
      BigDecimal.class,
      BigInteger.class
    ).map(Class::getName).collect(toSet());
  }

  public boolean check(String className) {
    return classNames.contains(className);
  }
}
