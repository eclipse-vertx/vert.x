/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.impl.future;

import io.vertx.core.Future;
import io.vertx.core.impl.ContextInternal;

/**
 * Expose some of the future internal stuff.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface FutureInternal<T> extends Future<T> {

  /**
   * @return the context associated with this promise or {@code null} when there is none
   */
  ContextInternal context();

  /**
   * Add a listener to the future result.
   *
   * @param listener the listener
   */
  void addListener(Listener<T> listener);

}
