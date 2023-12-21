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
package io.vertx.core.spi.context;

import io.vertx.core.impl.ContextKeyImpl;

/**
 * A context key to address local context data.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface ContextKey<T> {

  /**
   * Register a key.
   *
   * Keys should be registered prior creating a {@link io.vertx.core.Vertx} instance as a static field, once registered
   * a key cannot be unregistered.
   *
   * @param type the type of context data
   * @return the context key
   */
  static <T> ContextKey<T> registerKey(Class<T> type) {
    return new ContextKeyImpl<>();
  }
}
