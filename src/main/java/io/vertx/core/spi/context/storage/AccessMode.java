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

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Supplier;

/**
 * Defines the access mode of a context local storage.
 */
public interface AccessMode {

  /**
   * This access mode provides concurrent access to context local storage with thread safety and atomicity.
   */
  AccessMode CONCURRENT = new AccessMode() {

    @Override
    public Object get(AtomicReferenceArray<Object> locals, int idx) {
      return locals.get(idx);
    }

    @Override
    public void put(AtomicReferenceArray<Object> locals, int idx, Object value) {
      locals.set(idx, value);
    }

    @Override
    public Object getOrCreate(AtomicReferenceArray<Object> locals, int idx, Supplier<Object> initialValueSupplier) {
      Object res;
      while (true) {
        res = locals.get(idx);
        if (res != null) {
          break;
        }
        Object initial = initialValueSupplier.get();
        if (initial == null) {
          throw new IllegalStateException();
        }
        if (locals.compareAndSet(idx, null, initial)) {
          res = initial;
          break;
        }
      }
      return res;
    }
  };

  /**
   * Return the object at index {@code idx} in the {@code locals} array.
   * @param locals the array
   * @param idx the index
   * @return the object at {@code index}
   */
  Object get(AtomicReferenceArray<Object> locals, int idx);

  /**
   * Put {@code value} in the {@code locals} array at index {@code idx}
   * @param locals the array
   * @param idx the index
   * @param value the value
   */
  void put(AtomicReferenceArray<Object> locals, int idx, Object value);

  /**
   * Get or create the object at index {@code index} in the {@code locals} array. When the object
   * does not exist, {@code initialValueSupplier} must be called to obtain this value.
   *
   * @param locals the array
   * @param idx the index
   * @param initialValueSupplier the supplier of the initial value
   */
  Object getOrCreate(AtomicReferenceArray<Object> locals, int idx, Supplier<Object> initialValueSupplier);

}
