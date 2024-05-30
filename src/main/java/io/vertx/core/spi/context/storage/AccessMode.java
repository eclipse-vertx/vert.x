/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.spi.context.storage;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.Supplier;

/**
 * Defines the access mode of a context local storage.
 */
public interface AccessMode {

  /**
   * This access mode provides concurrent access to context local storage with thread safety and atomicity.
   */
  AccessMode CONCURRENT = ConcurrentAccessMode.INSTANCE;
  /**
   * Return the object at index {@code idx} in the {@code locals} array.
   * @param locals the array
   * @param idx the index
   * @return the object at {@code index}
   */
  Object get(Object[] locals, int idx);

  /**
   * Put {@code value} in the {@code locals} array at index {@code idx}
   * @param locals the array
   * @param idx the index
   * @param value the value
   */
  void put(Object[] locals, int idx, Object value);

  /**
   * Get or create the object at index {@code index} in the {@code locals} array. When the object
   * does not exist, {@code initialValueSupplier} must be called to obtain this value.
   *
   * @param locals the array
   * @param idx the index
   * @param initialValueSupplier the supplier of the initial value
   */
  Object getOrCreate(Object[] locals, int idx, Supplier<Object> initialValueSupplier);

}
