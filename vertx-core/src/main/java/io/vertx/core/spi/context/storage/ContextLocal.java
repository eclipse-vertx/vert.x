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

import io.vertx.core.Context;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.impl.ContextLocalImpl;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * <p>A local storage for arbitrary data attached to a {@link Context}.</p>
 *
 * <p>Local storage should be registered before creating a {@link io.vertx.core.Vertx} instance, once registered a
 * local storage cannot be unregistered.
 *
 * <p>It is recommended to initialize local storage as static fields of a {@link io.vertx.core.spi.VertxServiceProvider},
 * since providers are discovered before the capture of known local storages.
 *
 * <pre>{@code
 * public class CustomLocal implements VertxServiceProvider {
 *   public static final ContextLocal<CustomLocal> KEY = ContextLocal.registerLocal(CustomLocal.class);
 *   ...
 * }
 * }</pre>
 *
 * <p>Such provider must then be declared as a <a href="https://docs.oracle.com/javase/tutorial/sound/SPI-intro.html">Java service provider</a>
 * in {@code META/INF/services/io.vertx.core.spi.VertxServiceProvider} and optionally in a {@code module-info.java}.</p>
 *
 * <p>Context local can be used from a Vert.x {@link Context} with the following methods.</p>
 *
 * <ul>
 *   <li>{@link Context#getLocal(ContextLocal)}</li>
 *   <li>{@link Context#getLocal(ContextLocal, Supplier)}</li>
 *   <li>{@link Context#putLocal(ContextLocal, Object)}</li>
 *   <li>{@link Context#removeLocal(ContextLocal)}</li>
 *   <li>{@link Context#getLocal(ContextLocal, AccessMode)}</li>
 *   <li>{@link Context#getLocal(ContextLocal, AccessMode, Supplier)}</li>
 *   <li>{@link Context#putLocal(ContextLocal, AccessMode, Object)}</li>
 *   <li>{@link Context#removeLocal(ContextLocal, AccessMode)}</li>
 * </ul>
 *
 * <pre>{@code
 * context.putLocal(CustomLocal.KEY, new CustomLocal(...));
 * }</pre>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface ContextLocal<T> {

  /**
   * Registers a context local storage.
   *
   * @return the context local storage
   */
  static <T> ContextLocal<T> registerLocal(Class<T> type) {
    return ContextLocalImpl.create(type, Function.identity());
  }

  /**
   * Registers a context local storage.
   *
   * @return the context local storage
   */
  static <T> ContextLocal<T> registerLocal(Class<T> type, Function<T, T> duplicator) {
    return ContextLocalImpl.create(type, duplicator);
  }

  /**
   * Get the local data from the {@code context}.
   *
   * @return the local data
   */
  default T get(Context context) {
    return get(context, AccessMode.CONCURRENT);
  }

  /**
   * Get the local data from the {@code context}, when it does not exist then call {@code initialValueSupplier} to obtain
   * the initial value. The supplier can be called multiple times when several threads call this method concurrently.
   *
   * @param initialValueSupplier the supplier of the initial value
   * @return the local data
   */
  default T get(Context context, Supplier<? extends T> initialValueSupplier) {
    return get(context, AccessMode.CONCURRENT, initialValueSupplier);
  }

  /**
   * Put local data in the {@code context}.
   *
   * @param data  the data
   */
  default void put(Context context, T data) {
    put(context, AccessMode.CONCURRENT, data);
  }

  /**
   * Remove the local data from the context.
   */
  default void remove(Context context) {
    put(context, AccessMode.CONCURRENT, null);
  }

  /**
   * Like {@link #get(Context)} but with an {@code accessMode}.
   */
  default T get(Context context, AccessMode accessMode) {
    return ((ContextInternal)context).getLocal(this, accessMode);
  }

  /**
   * Like {@link #get(Context, Supplier)} but with an {@code accessMode}.
   */
  default T get(Context context, AccessMode accessMode, Supplier<? extends T> initialValueSupplier) {
    return ((ContextInternal)context).getLocal(this, accessMode, initialValueSupplier);
  }

  /**
   * Like {@link #put(Context, T)} but with an {@code accessMode}.
   */
  default void put(Context context, AccessMode accessMode, T value) {
    ((ContextInternal)context).putLocal(this, accessMode, value);
  }

  /**
   * Like {@link #remove(Context)} but with an {@code accessMode}.
   */
  default void remove(Context context, AccessMode accessMode) {
    put(context, accessMode, null);
  }

}
