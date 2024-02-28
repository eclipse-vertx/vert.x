/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.spi.context.storage;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.Context;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.ContextLocalImpl;

import java.util.function.Supplier;

/**
 * A context local storage to address local context data.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface ContextLocal<T> {

  /**
   * Registers a context local.
   *
   * <p>Locals should be registered before creating a {@link io.vertx.core.Vertx} instance, once registered a local cannot be unregistered.
   *
   * <p>It is recommended to initialize locals as static fields of a {@link io.vertx.core.spi.VertxServiceProvider}, since providers
   * are discovered before the capture of known locals.
   *
   * @param type the type of context data
   * @return the context key
   */
  static <T> ContextLocal<T> registerLocal(Class<T> type) {
    return new ContextLocalImpl<>();
  }

  /**
   * Get some local data from the context.
   *
   * @return the local data
   */
  @GenIgnore
  default T get(Context context) {
    return get(context, AccessMode.CONCURRENT);
  }

  /**
   * Get some local data from the context, when it does not exist the {@code initialValueSupplier} is called to obtain
   * the initial value.
   *
   * <p> The {@code initialValueSupplier} might be called multiple times when multiple threads call this method concurrently.
   *
   * @param initialValueSupplier the supplier of the initial value optionally called
   * @return the local data
   */
  @GenIgnore
  default T get(Context context, Supplier<? extends T> initialValueSupplier) {
    return get(context, AccessMode.CONCURRENT, initialValueSupplier);
  }

  /**
   * Put some local data in the context.
   * <p>
   * This can be used to share data between different handlers that share a context
   *
   * @param value  the data
   */
  @GenIgnore
  default void put(Context context, T value) {
    put(context, AccessMode.CONCURRENT, value);
  }

  /**
   * Remove some local data from the context.
   *
   */
  @GenIgnore
  default void remove(Context context) {
    put(context, AccessMode.CONCURRENT, null);
  }

  /**
   * Get some local data from the context.
   *
   * @return the local data
   */
  @GenIgnore
  default T get(Context context, AccessMode accessMode) {
    return ((ContextInternal)context).getLocal(this, accessMode);
  }

  /**
   * Get some local data from the context, when it does not exist the {@code initialValueSupplier} is called to obtain
   * the initial value.
   *
   * <p> The {@code initialValueSupplier} might be called multiple times when multiple threads call this method concurrently.
   *
   * @param initialValueSupplier the supplier of the initial value optionally called
   * @return the local data
   */
  @GenIgnore
  default T get(Context context, AccessMode accessMode, Supplier<? extends T> initialValueSupplier) {
    return ((ContextInternal)context).getLocal(this, accessMode, initialValueSupplier);
  }

  /**
   * Put some local data in the context.
   * <p>
   * This can be used to share data between different handlers that share a context
   *
   * @param value  the data
   */
  @GenIgnore
  default void put(Context context, AccessMode accessMode, T value) {
    ((ContextInternal)context).putLocal(this, accessMode, value);
  }

  /**
   * Remove some local data from the context.
   *
   */
  @GenIgnore
  default void remove(Context context, AccessMode accessMode) {
    put(context, accessMode, null);
  }

}
