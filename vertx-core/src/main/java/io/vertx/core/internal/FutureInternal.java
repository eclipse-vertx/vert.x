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
package io.vertx.core.internal;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.future.FutureBase;

/**
 * Expose some of the future internal stuff.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface FutureInternal<T> extends Future<T> {

  /**
   * Set an exception {@code handler} for exception thrown by future listeners during delivery on a given {@code thread},
   * this handler is called when the future is not associated with a context.
   *
   * @param thread the thread to report from
   * @param handler the handler or {@code null} to clear the subscription
   */
  static void exceptionHandler(Thread thread, Handler<Throwable> handler) {
    FutureBase.exceptionHandler(thread, handler);
  }

  /**
   * @return the context associated with this future or {@code null} when there is none
   */
  ContextInternal context();

}
