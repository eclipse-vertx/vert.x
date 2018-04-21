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
package io.vertx.core.spi.instrumentation;

import io.vertx.core.Handler;

/**
 * Lightweight instrumentation for Vert.x
 */
public interface Instrumentation {

  /**
   * Capture the current continuation and return a wrapper of the {@code handler}.
   * <p>
   * The returned wrapper will be called with the event instead of the original handler.
   * <p>
   * The wrapper can perform any activity around the original handler invocation.
   * <p>
   * The wrapper should not change the control of the handler invocation:
   * <ul>
   *   <li>the original handler must be called exactly once synchronously</li>
   *   <li>no unexpected throwable must be thrown by the wrapper</li>
   *   <li>any throwable thrown by the original handler must be propagated synchronously</li>
   * </ul>
   * The wrapper shall be prepared to receive nested calls.
   *
   * @param handler the handler to wrap
   * @return the wrapped handler
   */
  <T> Handler<T> captureContinuation(Handler<T> handler);

  /**
   * Unwrap and return the original handler from the {@code wrapper}.
   *
   * @param wrapper the handler wrapping the original handler
   * @return the original handler
   */
  <T> Handler<T> unwrapContinuation(Handler<T> wrapper);

}
